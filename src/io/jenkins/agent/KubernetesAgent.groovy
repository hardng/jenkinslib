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
      if (insideArgs?.trim()) {
        def matcher = insideArgs =~ /-v\s+([^:]+):([^\s]+)/
        matcher.each { all, hostPath, mountPath ->
          extraVolumes << hostPathVolume(mountPath: mountPath.trim(), hostPath: hostPath.trim())
        }
      }
    } else if (insideArgs instanceof List) {
      extraVolumes = insideArgs
    } else {

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
          containerTemplate(
            name: 'jnlp',
            image: 'jenkins/inbound-agent:latest',
            args: '${computer.jnlpmac} ${computer.name}',
            ttyEnabled: true
          ),
          containerTemplate(
            name: 'build',
            image: dockerImage,
            command: 'cat',
            ttyEnabled: true
          ),
          containerTemplate(
            name: 'buildkit',
            image: 'moby/buildkit:latest',
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
            def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
            script.echo "${Colors.CYAN}☸️ 使用 Kubernetes Agent 进行构建(镜像: ${dockerImage})${Colors.RESET}"1
            script.dir(projectDir) {
              script.unstash 'build-dir'
              script.container("build") {
                script.build_client.build(script.hook_funcs)
              }
              script.container('buildkit') {
                script.image_builer.buildImage()
              }
            }
          }
        }
      }

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
        - name: build
          image: 
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER, showRawYaml: showRawYaml, podRetention: podRetention, activeDeadlineSeconds: activeDeadlineSeconds) {
      
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