package io.jenkins.agent

import io.jenkins.agent.AgentInterface
import io.jenkins.common.Colors

class KubernetesAgent extends AgentInterface {

  KubernetesAgent(script) {
    super(script)
  }

  @Override
  void build(Map options = [:]) {
    def dockerImage = options.get('image') ?: 'moby/buildkit:latest'
    
    def activeDeadlineSeconds = 0
    def podRetention = script.onFailure()
    def showRawYaml = false
    if (script.env.LOG_DEBUG == 'true') {
      activeDeadlineSeconds = 360
      showRawYaml = true
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
        - name: rust
          image: hub.rancher8888.com/base/rust-base:latest
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
          volumeMounts:
            - name: sccache-cache
              mountPath: /root/.cache/sccache
        - name: maven
          image: hub.rancher8888.com/base/maven:3.8.8-openjdk-21-slim
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
          volumeMounts:
            - mountPath: "/root/.m2/"
              name: maven-cache
        - name: node
          image: node:16.19.1
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
          volumeMounts:
            - name: npm-cache
              mountPath: /root/.npm
            - name: pnpm-cache
              mountPath: /root/.pnpm-store
          securityContext:
            privileged: true
        - name: buildkit
          image: moby/buildkit:latest
          imagePullPolicy: IfNotPresent
          securityContext:
            privileged: true
        volumes:
        - name: maven-cache
          hostPath:
            path: "/tmp/m2"
        - name: npm-cache
          hostPath:
            path: "/tmp/.npm-cache"
        - name: pnpm-cache
          hostPath:
            path: "/tmp/.pnpm-store"
        - name: cargo-cache
          hostPath:
            path: "/tmp/.cargo"
        - name: sccache-cache
          hostPath:
            path: "/tmp/.cache/sccache"
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER, showRawYaml: showRawYaml, podRetention: podRetention, activeDeadlineSeconds: activeDeadlineSeconds) {
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace {
          def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
          script.echo "${Colors.CYAN}☸️ 使用 Kubernetes Agent 进行构建${Colors.RESET}"

          def containerName = getContainerByProgramming(script.env.PROGRAMMING)

          script.dir(projectDir) {
            script.unstash 'build-dir'
            script.container(containerName) {
              script.build_client.build(script.hook_funcs)
            }

            script.container('buildkit') {
              script.image_builer.buildImage()
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