package io.jenkins.agent

import io.jenkins.agent.AgentInterface

class DockerAgent implements AgentInterface {
  def script

  DockerAgent(script) {
    this.script = script
  }

  @Override
  void build(Map hookFuncs) {
    def dockerImage = 'hub.rancher8888.com/base/compilation:v0.0.1'
    def PROJECT_DIR = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
    script.node {
      script.echo "${script.vars.green}🐳 使用 Docker Agent 编译${script.vars.reset}"
      script.docker.image(dockerImage).inside('-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins') {
        if (PROJECT_DIR) {
          script.dir(PROJECT_DIR) {
            script.build_client.build(hookFuncs)
          }
        } else {
          script.build_client.build(hookFuncs)
        }
      }
    }
  }

  @Override
  void buildImage() {
    def dockerImage = 'moby/buildkit:latest'
    script.node {
      script.echo "${script.vars.green}🐳 使用 Docker Agent 建制镜像${script.vars.reset}"
      script.docker.image(dockerImage).inside('-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins') {
        script.image_builer.buildImage()
      }
    }
  }

  @Override
  void deploy() {
    def dockerImage = 'roffe/kubectl'
    script.node {
      script.echo "${script.vars.cyan}🐳 使用 Docker Agent 部署${script.vars.reset}"
      script.docker.image(dockerImage).inside('-v /root/.cargo:/root/.cargo -v /root/.m2:/root/.m2 -v /root/.jenkins:/root/.jenkins') {
        script.deploy_client.mainDeployStage()
      }
    }
  }
}
