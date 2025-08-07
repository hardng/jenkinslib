package io.jenkins.agent

import io.jenkins.agent.AgentInterface

class DockerAgent extends AgentInterface {

  DockerAgent(script) {
    super(script)
  }

  @Override
  void build(Map options = [:]) {
    def dockerImage = options.get('image') ?: 'rust:1.88-slim-bullseye'
    def insideArgs = options.get('insideArgs') ?: '-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins'
    def hookFuncs = options.get('hookFuncs', [:])
    def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"

    script.node {
      script.echo "${script.vars.green}🐳 使用 Docker Agent 编译 (镜像: ${dockerImage})${script.vars.reset}"
      script.docker.image(dockerImage).inside(insideArgs) {
        if (projectDir?.trim()) {
          script.dir(projectDir) {
            script.build_client.build(hookFuncs)
          }
        } else {
          script.build_client.build(hookFuncs)
        }
      }
    }
  }

  @Override
  void buildImage(Map options = [:]) {
    def dockerImage = options.get('image') ?: 'moby/buildkit:latest'
    def insideArgs = options.get('insideArgs') ?: '-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins'

    script.node {
      script.echo "${script.vars.green}🐳 使用 Docker Agent 建制镜像 (镜像: ${dockerImage})${script.vars.reset}"
      script.docker.image(dockerImage).inside(insideArgs) {
        script.image_builer.buildImage()
      }
    }
  }

  @Override
  void deploy(Map options = [:]) {
    def dockerImage = options.get('image') ?: 'roffe/kubectl'
    def insideArgs = options.get('insideArgs') ?: '-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins'

    script.node {
      script.echo "${script.vars.cyan}🐳 使用 Docker Agent 部署 (镜像: ${dockerImage})${script.vars.reset}"
      script.docker.image(dockerImage).inside(insideArgs) {
        script.deploy_client.mainDeployStage()
      }
    }
  }
}
