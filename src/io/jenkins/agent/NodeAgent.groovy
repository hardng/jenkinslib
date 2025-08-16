package io.jenkins.agent

import io.jenkins.agent.AgentInterface
import io.jenkins.common.Colors

class NodeAgent extends AgentInterface {
  NodeAgent(script) {
    super(script)
  }

  @Override
  void build(Map options = [:]) {
    def hookFuncs = options.get('hookFuncs', [:])
    script.node {
      script.echo "${Colors.GREEN}🖥️ 使用 Node Agent 进行构建${Colors.RESET}"
      def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
      
      if (projectDir?.trim()) {
        script.dir(projectDir) {
          script.build_client.build(hookFuncs)
          if(script.env.PLATFORM == "kubernetes") {
            script.image_builer.buildImage()
          }
        }
      } else {
        script.build_client.build(hookFuncs)
      }
    }
  }

  @Override
  void buildImage(Map options = [:]) {
    def hookFuncs = options.get('hookFuncs', [:])
    script.node {
      script.echo "${Colors.GREEN}🖥️ 使用 Node Agent 构建镜像${Colors.RESET}"
      script.build_client.build(hookFuncs)
    }
  }

  @Override
  void deploy(Map options = [:]) {
    script.common.withAgentWorkspace {
      script.node {
        script.echo "${Colors.CYAN}🖥️ 使用 Node Agent 部署${Colors.RESET}"
        script.deploy_client.mainDeployStage()
      }
    }
  }

}
