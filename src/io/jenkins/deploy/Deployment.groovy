// src/io/jenkins/deploy/Deployment.groovy
package io.jenkins.deploy

import io.jenkins.common.Colors

class Deployment implements Serializable {
  private transient script

  private Deployment(script) {
    this.script = script
  }

  static Deployment getInstance(script) {
    return new Deployment(script)
  }

  // 设置 Kubernetes Namespace
  def setKubernetesNamespace(manifestFile) {
    if (!script.fileExists(manifestFile)) {
      script.error "❌ Manifest文件不存在: ${manifestFile}"
    }

    def yamlText = script.readFile file: manifestFile
    def yamls = script.readYaml text: yamlText

    def namespaceList = yamls
      .findAll { it?.metadata?.namespace }
      .collect { it.metadata.namespace }
      .unique()
      .findAll { it }

    script.env.KUBE_NAMESPACE = namespaceList.size() == 0 ? 'prod' : namespaceList[0]
  }

  boolean checkBuildArtifactExists(String path) {
    // find 不会自动展开通配符，所以先提取目录部分，再用 find 判断有无文件
    def checkScript = """
      if ! test -n "\$(find ${path} -type f -print -quit)"; then
        echo '❌ 构建产物为空或不存在: ${path}'
        exit 1
      fi
    """

    def exists = script.sh(script: checkScript, returnStatus: true) == 0

    return exists
  }

  // 判断是否需要重启
  def checkIfNeedRestart() {
    return (script.env.EXEC_RESULT?.toBoolean() ?: false) &&
          (script.env.CURRENT_COMMIT_ID ?: '') == (script.env.PREVIOUS_COMMIT_ID ?: '') &&
          (script.env.SAME_MODULES?.toBoolean() ?: false) &&
          (script.env.PREVIOUS_BUILD_SUCCESS?.toBoolean() ?: false) &&
          (script.env.PREVIOUS_IMAGE_UPLOADED?.toBoolean() ?: false) &&
          (script.env.FORCE_BUILD?.toBoolean() == false)
  }

  // Kubernetes 部署部分
  def deployToKubernetes(projectName, module, image_addr, manifestFile) {
    def deployFile = "deploy-${module}.yaml"
    script.sh """
      set -e
      set +x
      cp ${manifestFile} ${deployFile}
      sed -i "s#IMAGE#${image_addr}#g" ${deployFile}
    """

    def deploymentExists = script.sh(
      script: "kubectl get deployment ${projectName} -n ${script.env.KUBE_NAMESPACE} > /dev/null 2>&1 && echo yes || echo no",
      returnStdout: true
    ).trim()

    if (deploymentExists == "no") {
      script.sh "kubectl apply -f ${deployFile} -n ${script.env.KUBE_NAMESPACE}"
    } else {
      // 判断是否有变化
      def diffResult = script.sh(
        script: "kubectl diff -f ${deployFile} -n ${script.env.KUBE_NAMESPACE} || true",
        returnStdout: true
      ).trim()

      if (diffResult) {
        script.sh "kubectl apply -f ${deployFile} -n ${script.env.KUBE_NAMESPACE}"
      } else {
        restartKubernetesDeployment(projectName)
      }
    }
    script.sh "rm -f ${deployFile}"
  }


  // Kubernetes 重启逻辑
  def restartKubernetesDeployment(projectName) {
    script.sh """
      set +x
      set -e
      kubectl rollout restart deployment/${projectName} -n \${KUBE_NAMESPACE}
    """
  }

  def watchKubernetesDeployment(projectName) {
      script.sh """
        set -e
        set +x
        
        # 检查 deployment rollout 状态
        if ! kubectl rollout status deployment/${projectName} -n \${KUBE_NAMESPACE} --timeout=300s; then
            echo "${Colors.RED}❌ 部署超时或失败${Colors.RESET}"
            kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} -o jsonpath='{range .items[*]}{.metadata.name}{"\\n"}{end}' \\
            | while read pod; do
                if [ -n "\$pod" ]; then
                  kubectl get events --field-selector involvedObject.name=\$pod,involvedObject.kind=Pod -n \${KUBE_NAMESPACE} \\
                  | sort -k1,1
                fi
            done
            exit 1
        fi
        
        echo "${Colors.YELLOW}🔄 Deployment rollout 成功，正在检查 Pod 就绪状态...${Colors.RESET}"
        
        # 获取期望的副本数
        desired_replicas=\$(kubectl get deployment ${projectName} -n \${KUBE_NAMESPACE} -o jsonpath='{.spec.replicas}'| tr -d '[:space:]')
        echo "期望副本数: \$desired_replicas"
        
        # 循环检查，直到有足够数量的非 Terminating Pod 处于 Ready 状态
        timeout=300
        interval=5
        elapsed=0
        
        while [ \$elapsed -lt \$timeout ]; do
          if kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} --no-headers | grep -q .; then
              ready_count=\$(kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} --no-headers | \
                awk '\$2 ~ /^[0-9]+\\/[0-9]+\$/ {split(\$2, a, "/"); if(a[1]==a[2] && \$3=="Running") count++} END {print count+0}' | \
                tr -d '[:space:]')
              # 排除Terminating的Pod
              active_pods=\$(kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} --no-headers | grep -v Terminating | wc -l | tr -d '[:space:]')
              
              echo "当前状态: Ready Pod 数量 \$ready_count/\$desired_replicas (活跃Pod: \$active_pods)"
              
              if [ "\$ready_count" -eq "\$desired_replicas" ] && [ "\$active_pods" -eq "\$desired_replicas" ]; then
                echo "${Colors.GREEN}✅ 部署成功，所有 Pod 已就绪: \$ready_count/\$desired_replicas${Colors.RESET}"
                break
              fi
          else
              echo "未找到匹配标签 app=${projectName} 的Pod，等待Pod创建..."
          fi
          
          sleep \$interval
          elapsed=\$((elapsed + interval))
        done
        
        # 超时检查
        if [ \$elapsed -ge \$timeout ]; then
          echo "${Colors.RED}❌ Pod 就绪检查超时${Colors.RESET}"
          echo "详细状态:"
          kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} --no-headers
          exit 1
        fi
      """
  }

  // VM 部署逻辑
  def deployToVM(projectName, buildPath, command) {
      def remoteDir = script.env.DESTINATION_DIR ?: "/data"
      def remoteHost = script.env.DESTINATION_HOST
      if (!remoteHost) script.error "VM部署需要配置 destination_host"
      if ((!checkBuildArtifactExists(buildPath))) script.error "构建产物不存在: ${buildPath}"
      // 安全方法
      def pathSegments = buildPath.split('/')
      def dirPrefix = pathSegments[0..<(pathSegments.length - 1)].join('/')
      def fileName = buildPath.substring(buildPath.lastIndexOf('/') + 1)

      script.dir(dirPrefix){
        try {
          script.sshPublisher(
            continueOnError: false,
            failOnError: true,
            publishers: [
              script.sshPublisherDesc(
                configName: "${remoteHost}",
                verbose: true,
                transfers: [
                  script.sshTransfer(
                    sourceFiles: "${fileName}",
                    // 不安全的方法
                    // removePrefix: buildPath.split('/').dropRight(1).join('/'),
                    remoteDirectory: "${remoteDir}/${projectName}",
                    makeEmptyDirs: true
                  )
                ]
              )
            ]
          )
        } catch (Exception e) {
          script.error "❌ 终止流水线 ${e.getMessage()}"
        } 
      }
      if (command && command != "") {
        executeVMCommand(projectName, remoteHost, remoteDir, command)
      }
  }

  // VM 重启服务
  def restartVMService(projectName, command) {
      def remoteDir = script.env.DESTINATION_DIR ?: "/data"
      def remoteHost = script.env.DESTINATION_HOST
      if (!remoteHost) script.error "VM重启需要配置 destination_host"

      if (command && command != "") {
        executeVMCommand(projectName, remoteHost, remoteDir, command)
      } else {
        script.echo "${Colors.YELLOW}⚠️ 未配置exec_command，跳过VM重启${Colors.RESET}"
      }
  }

  // VM 命令执行和状态检查
  def executeVMCommand(projectName, remoteHost, remoteDir, command) {
    try {
      script.sshPublisher(
        continueOnError: false,
        failOnError: true,
        publishers: [
          script.sshPublisherDesc(
            verbose: true,
            configName: remoteHost,
            transfers: [
              script.sshTransfer(
                sourceFiles: "scripts/run.sh",
                removePrefix: "scripts",
                remoteDirectory: "${remoteDir}",
                execCommand: "chmod +x ${remoteDir}/run.sh && ${command} --language ${script.env.PROGRAMMING}",
                makeEmptyDirs: true
              )
            ]
          )
        ]
      )
    } catch (Exception e) {
      script.error "❌ 终止流水线 ${e.getMessage()}"
    } 
  }

  def deployActionExectionMain(mod, project_name, manifest_file, path, results) {
    def command_list = script.env.EXEC_COMMAND?.trim() ? script.readJSON(text: script.env.EXEC_COMMAND) : [:]
    def needRestart  = checkIfNeedRestart()
    return {
      try {
        if (script.env.PLATFORM == "kubernetes") {
          setKubernetesNamespace(manifest_file)
          def image_tag = script.env.CURRENT_COMMIT_ID
          def image_addr = (script.env.SHARED_MODULE == 'true') ?
              "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}:${image_tag}" :
              "${script.env.DOCKER_REGISTRY}/${script.env.JOB_PREFIX}-${mod}:${image_tag}"

          if (needRestart) {
            restartKubernetesDeployment(project_name)
          } else {
            deployToKubernetes(project_name, mod, image_addr, manifest_file)
            watchKubernetesDeployment(project_name)
          }
        } else if (script.env.PLATFORM == "vm") {
          if (needRestart) {
            restartVMService(project_name, command_list[mod]?.toString() ?: "")
          } else {
            deployToVM(project_name, path, command_list[mod]?.toString() ?: "")
          }
        }
        script.echo "${Colors.GREEN}✅ 模块 ${project_name} ${needRestart ? '重启' : '发布'}成功${Colors.RESET}"
        results[project_name] = "SUCCESS"
      } catch (Exception e) {
        script.echo "${Colors.RED}❌ 模块 ${project_name} ${needRestart ? '重启' : '发布'}失败${Colors.RESET}"
        results[project_name] = "FAILURE: ${e.getMessage()}"
        script.error "${e.getMessage()}"
      }
    }
  }

// 并行部署
  def deployModules() {
    def module_list = []
    if (script.params.MODULES) {
      module_list = script.params.MODULES.split(',')
          .collect { it.trim() }
          .findAll { it }
    }
    def app_module = script.readJSON text: script.env.APP_MODULE

    def tasks   = [:]
    def results = [:]
    // 默认不开启并行
    def isParallel = script.env.IS_PARALLEL?.toBoolean()

    for (m in module_list) {
      def mod = m
      def subpath = app_module[mod]?.toString() ?: ""
      def path = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}/${subpath}"
      def project_name
      def manifest_file

      if (script.env.JOB_PREFIX != "") {
        if (script.env.MANIFEST_PREFIX != "") {
          manifest_file = "${script.env.ROOT_WORKSPACE}/manifests/${script.env.JOB_PREFIX}-${script.env.MANIFEST_PREFIX}-${mod}.yaml"
          project_name  = "${script.env.JOB_PREFIX}-${script.env.MANIFEST_PREFIX}-${mod}"
        } else {
          manifest_file = "${script.env.ROOT_WORKSPACE}/manifests/${script.env.JOB_PREFIX}-${mod}.yaml"
          project_name  = "${script.env.JOB_PREFIX}-${mod}"
        }
      } else {
        if (script.env.MANIFEST_PREFIX != "") {
          manifest_file = "${script.env.ROOT_WORKSPACE}/manifests/${script.env.MANIFEST_PREFIX}-${mod}.yaml"
          project_name  = "${mod}"
        } else {
          manifest_file = "${script.env.ROOT_WORKSPACE}/manifests/${mod}.yaml"
          project_name  = "${mod}"
        }
      }

      if (isParallel) {
        def agent_type = script.env.BUILD_PLATFORM ? script.env.BUILD_PLATFORM : (script.env.PLATFORM ? script.env.PLATFORM : 'any')
        def agent = script.agent_mgr.getAgent(agent_type)
        tasks[project_name] = {
          script.stage("${project_name}") {
            agent.runInKubernetesAgent([image: "roffe/kubectl"]) {
              deployActionExectionMain(mod, project_name, manifest_file, path, results).call()
            }
          }
        }
      } else {
        // 串行模式，直接执行即可
        deployActionExectionMain(mod, project_name, manifest_file, path, results).call()
      }
    }

    // 配置了并行就并行操作，否则单行
    if (isParallel && tasks) {
      script.echo "🔀 并行部署模块: ${tasks.keySet()}"
      script.parallel(tasks + [failFast: false])
    }

    script.echo "================ 📊 部署结果汇总 ================"
    results.each { k, v ->
      script.echo "${k}: ${v}"
    }
  }


  // 发布阶段的入口函数
  def mainDeployStage() {
    // 封装调用逻辑
    if (script.env.PLATFORM == "kubernetes") {
      // 只能使用一次withCredentials 块处理 KUEBCONFIG 变量
      script.withCredentials([script.file(credentialsId: "${script.env.KUBECONFIG}", variable: 'KUBECONFIG')]) {
        deployModules()
      }
    } else {
      deployModules()
    }
  }

}