package io.jenkins.agent

import io.jenkins.agent.AgentInterface
import io.jenkins.common.Colors

class KubernetesAgent extends AgentInterface {

  KubernetesAgent(script) {
    super(script)
  }

  @Override
  void build(Map options = [:]) {
    def dockerImage  = options.get('image') ?: 'moby/buildkit:latest'
    def insideArgs   = options.get('insideArgs') ?: ''
    // 处理：字符串转 DSL
    def extraVolumes = []
    if (insideArgs instanceof String) {
      if (insideArgs) {
        // 支持多个 -v 参数
        def volumes = (insideArgs =~ /-v\s+([^:]+):([^\s]+)/).findAll()
        volumes.each { v ->
          def hostPath  = v[1]?.trim()
          def mountPath = v[2]?.trim()
          if (hostPath && mountPath) {
            extraVolumes << script.hostPathVolume(mountPath: mountPath, hostPath: hostPath)
          } else {
            script.echo "${Colors.YELLOW}⚠️ Volume 参数解析失败: ${v}${Colors.RESET}"
          }
        }
      }
    } else if (insideArgs instanceof List) {
      extraVolumes = insideArgs
    }

    def activeDeadlineSeconds = 0
    def podRetention = script.onFailure()
    def showRawYaml = false
    if (script.env.LOG_DEBUG == 'true') {
      activeDeadlineSeconds = 360
      showRawYaml = true
    }
    script.podTemplate(
      containers: [
        script.containerTemplate(
          name: 'jnlp',
          image: 'jenkins/inbound-agent:latest',
          args: '${computer.jnlpmac} ${computer.name}',
          ttyEnabled: true
        ),
        script.containerTemplate(
          name: 'build',
          image: dockerImage,
          command: 'cat',
          ttyEnabled: true
        ),
        script.containerTemplate(
          name: 'buildkit',
          image: 'moby/buildkit:latest',
          privileged: true
        )
      ],
      volumes: extraVolumes,
      cloud: script.env.DEPLOY_CLUSTER,
      showRawYaml: showRawYaml,
      podRetention: podRetention,
      activeDeadlineSeconds: activeDeadlineSeconds
    ){
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace {
          def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
          script.echo "${Colors.CYAN}☸️ 使用 Kubernetes Agent 进行构建(镜像: ${dockerImage})${Colors.RESET}"
          script.dir(projectDir) {
            script.unstash 'build-dir'
            script.container("build") {
              script.build_client.build(script.hook_funcs)
            }
            if(script.env.SKIP_BUILD_IMG != "true") {
              script.container('buildkit') {
                script.image_builer.buildImage()
              }
            }
          }
        }
      }
    }
  }

  @Override
  void buildImage(Map options = [:]) {

    def activeDeadlineSeconds = 0
    def podRetention = script.onFailure()
    def showRawYaml = false
    if (script.env.LOG_DEBUG == 'true') {
      activeDeadlineSeconds = 360
      showRawYaml = true
    }

    def dockerImage = options.get('image') ?: 'moby/buildkit:latest'
    def podTemplate = """
      apiVersion: v1
      kind: Pod
      metadata:
        name: jenkins-slave
        namespace: kube-ops
      spec:
        imagePullSecrets:
        - name: pull-image
        containers:
        - name: jnlp
          image: jenkins/inbound-agent:latest
          imagePullPolicy: IfNotPresent
          args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
        - name: buildkit
          image: ${dockerImage}
          imagePullPolicy: IfNotPresent
          securityContext:
            privileged: true
        volumes:
        - name: maven-cache
          hostPath:
            path: "/tmp/m2"
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER, showRawYaml: showRawYaml, podRetention: podRetention, activeDeadlineSeconds: activeDeadlineSeconds) {
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace {
          script.echo "${Colors.CYAN}☸️ 使用 Kubernetes Agent 进行镜像构建${Colors.RESET}"
          script.container('buildkit') {
            script.image_builer.buildImage()
          }
        }
      }
    }
  }

  @Override
  void deploy(Map options = [:]) {
    def activeDeadlineSeconds = 0
    def podRetention = script.onFailure()
    def showRawYaml = false
    if (script.env.LOG_DEBUG == 'true') {
      activeDeadlineSeconds = 360
      showRawYaml = true
    }

    def dockerImage = options.get('image') ?: 'roffe/kubectl'
    def podTemplate = """
      apiVersion: v1
      kind: Pod
      metadata:
        name: jenkins-slave
        namespace: kube-ops
      spec:
        imagePullSecrets:
        - name: pull-image
        containers:
        - name: jnlp
          image: jenkins/inbound-agent:latest
          imagePullPolicy: IfNotPresent
          args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
        - name: kubectl
          image: ${dockerImage}
          imagePullPolicy: IfNotPresent
          command: [cat]
          tty: true
          securityContext:
            runAsUser: 0
        volumes:
        - name: maven-cache
          hostPath:
            path: "/tmp/m2"
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER, showRawYaml: showRawYaml, podRetention: podRetention, activeDeadlineSeconds: activeDeadlineSeconds) {
      script.common.withAgentWorkspace {
        script.node(script.POD_LABEL) {
          script.container('kubectl') {
            script.deploy_client.mainDeployStage()
          }
        }
      }
    }
  }

  /**
    * 在kubernetes Agent中执行逻辑
    *
    * @param options     动态参数，比如 image，挂载目录
    * @param body        要执行的action
  * */
  def runInKubernetesAgent(Map options = [:], Closure body) {
    def actionName   = options.get('name') ?: "dynamic-action"
    def dockerImage  = options.get('image') ?: 'bitnami/kubectl'
    def insideArgs   = options.get('insideArgs') ?: ''

    // 处理：字符串转 DSL
    def extraVolumes = []
    if (insideArgs instanceof String) {
      if (insideArgs) {
        // 支持多个 -v 参数
        def volumes = (insideArgs =~ /-v\s+([^:]+):([^\s]+)/).findAll()
        volumes.each { v ->
          def hostPath  = v[1]?.trim()
          def mountPath = v[2]?.trim()
          if (hostPath && mountPath) {
            extraVolumes << script.hostPathVolume(mountPath: mountPath, hostPath: hostPath)
          } else {
            script.echo "${Colors.YELLOW}⚠️ Volume 参数解析失败: ${v}${Colors.RESET}"
          }
        }
      }
    } else if (insideArgs instanceof List) {
      extraVolumes = insideArgs
    }

    def activeDeadlineSeconds = 0
    def podRetention = script.onFailure()
    def showRawYaml = false
    if (script.env.LOG_DEBUG == 'true') {
      activeDeadlineSeconds = 3600
      podRetention = script.always()
      showRawYaml = true
    }
    script.podTemplate(
      containers: [
        script.containerTemplate(
          name: 'jnlp',
          image: 'jenkins/inbound-agent:latest',
          args: '${computer.jnlpmac} ${computer.name}',
          ttyEnabled: true
        ),
        script.containerTemplate(
          name: actionName,
          image: dockerImage,
          command: 'cat',
          ttyEnabled: true
        )
      ],
      volumes: extraVolumes,
      cloud: script.env.DEPLOY_CLUSTER,
      showRawYaml: showRawYaml,
      podRetention: podRetention,
      activeDeadlineSeconds: activeDeadlineSeconds
    ){
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace {
          def projectDir = "${script.env.WORKSPACE}"
          script.echo "${Colors.CYAN}☸️ 使用Kubernetes Agent执行动态任务 [${actionName}] (镜像: ${dockerImage})${Colors.RESET}"
          script.dir(projectDir) {
            script.container(actionName) {
              body()
            }
          }
        }
      }
    }
  }

  String getContainerByProgramming(String programming) {
    switch(programming?.toLowerCase()) {
      case 'java':
      case 'maven':
        return 'maven'
      case 'frontend':
      case 'vue':
      case 'js':
      case 'node':
        return 'node'
      case 'rust':
        return 'rust'
      case 'docker':
        return 'buildkit'
      case 'kubectl':
      case 'kubernetes':
      case 'k8s':
        return 'kubectl'
      default:
        return 'maven'
    }
  }
}