// utils/deployment.groovy

def vars

// 设置 Kubernetes Namespace
def setKubernetesNamespace(manifestFile) {
  if (!fileExists(manifestFile)) {
    error "❌ Manifest文件不存在: ${manifestFile}"
  }

  def yamlText = readFile file: manifestFile
  def yamls = readYaml text: yamlText

  def namespaceList = yamls
    .findAll { it?.metadata?.namespace }
    .collect { it.metadata.namespace }
    .unique()
    .findAll { it }

  env.KUBE_NAMESPACE = namespaceList.size() == 0 ? 'prod' : namespaceList[0]
}

// 判断是否需要重启
def checkIfNeedRestart() {
  def currentCommit = env.CURRENT_COMMIT_ID
  def previousCommit = env.PREVIOUS_COMMIT_ID ?: ""
  return previousCommit == currentCommit && previousCommit != ""
}

// Kubernetes 部署逻辑
def deployToKubernetes(projectName, module, image_addr, manifestFile) {
  sh """
    set +x
    set -e
    cp ${manifestFile} deploy-${module}.yaml
    sed -i "s#IMAGE#${image_addr}#g" deploy-${module}.yaml
    kubectl apply -f deploy-${module}.yaml -n \${KUBE_NAMESPACE}
    rm -f deploy-${module}.yaml
  """
}

// Kubernetes 重启逻辑
def restartKubernetesDeployment(projectName) {
  sh """
    set +x
    set -e
    kubectl rollout restart deployment/${projectName} -n \${KUBE_NAMESPACE}
  """
}

// Kubernetes 部署状态检查
def watchKubernetesDeployment(projectName) {
  sh """
    set -e
    set +x
    if ! kubectl rollout status deployment/${projectName} -n \${KUBE_NAMESPACE} --timeout=180s; then
      echo "${color_vars.RED}❌ 部署超时或失败${vars.reset}"
      kubectl get pods -l app=${projectName} -n prod -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
      | while read pod; do
        kubectl get events --field-selector involvedObject.name=\$pod,involvedObject.kind=Pod -n prod \
        | sort -k1,1
      done
      exit 1
    fi

    if ! kubectl wait --for=condition=Ready pod -l app=${projectName},pod-template-hash=\$(kubectl get rs -n \${KUBE_NAMESPACE} -l app=${projectName} --sort-by=.metadata.creationTimestamp -o jsonpath='{.items[-1:].metadata.name}' | awk -F'-' '{print \$NF}') -n \${KUBE_NAMESPACE} --timeout=180s; then
      kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE}
      exit 1
    fi
    ready_pods=\$(kubectl get pods -l app=${projectName} -n \${KUBE_NAMESPACE} -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' | grep -o 'True' | wc -l)
    echo "${color_vars.GREEN}✅ 部署成功，运行中且就绪的 Pod 数量: \$ready_pods${vars.reset}"
  """
}

// VM 部署逻辑
def deployToVM(projectName, buildPath) {
    def remoteDir = env.DESTNATION_DIR ?: "/data"
    def remoteHost = env.DESTNATION_HOST
    if (!remoteHost) error "VM部署需要配置 destnation_host"
    if (!fileExists(buildPath)) error "构建产物不存在: ${buildPath}"

    sshPublisher(
      publishers: [
        sshPublisherDesc(
          configName: remoteHost,
          transfers: [
            sshTransfer(
              execCommand: "mkdir -p ${remoteDir}/${projectName}",
              verbose: true
            ),
            sshTransfer(
              sourceFiles: "${buildPath}/**",
              removePrefix: buildPath.split('/').dropRight(1).join('/'),
              remoteDirectory: "${remoteDir}/${projectName}",
              verbose: true
            )
          ],
          verbose: true,
          failOnError: true
        )
      ]
    )

    if (env.EXEC_COMMAND && env.EXEC_COMMAND != "") {
        executeVMCommand(projectName, remoteHost, remoteDir, false)
    }
}

// VM 重启服务
def restartVMService(projectName) {
    def remoteDir = env.DESTNATION_DIR ?: "/opt/apps"
    def remoteHost = env.DESTNATION_HOST
    if (!remoteHost) error "VM重启需要配置 destnation_host"

    if (env.EXEC_COMMAND && env.EXEC_COMMAND != "") {
        executeVMCommand(projectName, remoteHost, remoteDir, true)
    } else {
        echo "⚠️ 未配置exec_command，跳过VM重启"
    }
}

// VM 命令执行和状态检查
def executeVMCommand(projectName, remoteHost, remoteDir, needRestart) {
    def startScript = ""
    writeFile file: "start-${projectName}.sh", text: startScript
    sshPublisher(
        publishers: [
            sshPublisherDesc(
                configName: remoteHost,
                transfers: [
                    sshTransfer(
                        sourceFiles: "start-${projectName}.sh",
                        remoteDirectory: "${remoteDir}/${projectName}",
                        execCommand: "cd ${remoteDir}/${projectName} && chmod +x start-${projectName}.sh && ./start-${projectName}.sh",
                        verbose: true
                    )
                ],
                verbose: true,
                failOnError: true
            )
        ]
    )
}

// 发布阶段的入口函数
def mainDeployStage() {
  def module_list = params.MODULES?.split(',')
  def app_module = readJSON text: env.APP_MODULE
  def needRestart = checkIfNeedRestart()
  vars = load 'utils/vars.groovy'
  // 只能使用一次withCredentials 块处理 KUEBCONFIG 变量
  withCredentials([file(credentialsId: "${env.KUBECONFIG}", variable: 'KUBECONFIG')]) {
    for (mod in module_list) {
      def subpath = app_module[mod]?.toString() ?: ""
      def path = "${env.ROOT_WORKSPACE}/${env.MAIN_PROJECT}/${subpath}"
      def project_name = "${env.JOB_PREFIX}-${mod}"
      def manifest_file = "${env.ROOT_WORKSPACE}/manifests/${env.JOB_PREFIX}-${mod}.yaml"

      try {
        if (env.PLATFORM == "kubernetes") {
          setKubernetesNamespace(manifest_file)
          def image_tag = env.CURRENT_COMMIT_ID
          def image_addr = "${env.DOCKER_REGISTRY}/${project_name}:${image_tag}"
          if (needRestart) {
            restartKubernetesDeployment(project_name)
          } else {
            deployToKubernetes(project_name, mod, image_addr, manifest_file)
            watchKubernetesDeployment(project_name)
          }
        } else if (env.PLATFORM == "vm") {
          if (needRestart) {
            restartVMService(project_name)
          } else {
            deployToVM(project_name, path)
          }
        }
        echo "${color_vars.GREEN}✅ 模块 ${project_name} ${needRestart ? '重启' : '发布'}成功${vars.reset}"
      } catch (Exception e) {
        echo "${color_vars.RED}❌ 模块 ${project_name} ${needRestart ? '重启' : '发布'}失败${vars.reset}"
        error "${e.getMessage()}"
      }
    }
  }
}

return this