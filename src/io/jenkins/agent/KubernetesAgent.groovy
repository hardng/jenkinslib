package io.jenkins.agent

import io.jenkins.agent.AgentInterface

class KubernetesAgent extends AgentInterface {

  KubernetesAgent(script) {
    super(script)
  }

  @Override
  void build(Map hookFuncs = [:]) {
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
          image: hub.rancher8888.com/base/recharge-rust-base:latest
          imagePullPolicy: IfNotPresent
          command:
          - cat
          tty: true
          volumeMounts:
            - name: cargo-cache
              mountPath: /root/.cargo
            - name: sccache-cache
              mountPath: /root/.cache
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
            path: "/tmp/.sccache"
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER, showRawYaml: false) {
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace(script) {
          def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
          script.echo "${script.vars.cyan}☸️ 使用 Kubernetes Agent 进行构建${script.vars.reset}"

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
  void buildImage() {
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
          image: moby/buildkit:latest
          imagePullPolicy: IfNotPresent
          securityContext:
            privileged: true
        volumes:
        - name: maven-cache
          hostPath:
            path: "/tmp/m2"
    """.stripIndent()

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER) {
      script.node(script.POD_LABEL) {
        script.common.withAgentWorkspace(script) {
          script.echo "${script.vars.cyan}☸️ 使用 Kubernetes Agent 进行镜像构建${script.vars.reset}"
          script.container('buildkit') {
            script.image_builer.buildImage()
          }
        }
      }
    }
  }

  @Override
  void deploy() {
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
          image: hub.rancher8888.com/base/devops-tools:v0.0.1
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

    script.podTemplate(yaml: podTemplate, cloud: script.env.DEPLOY_CLUSTER) {
      script.common.withAgentWorkspace(script) {
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