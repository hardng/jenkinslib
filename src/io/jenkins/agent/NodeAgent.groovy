package io.jenkins.agent

import io.jenkins.agent.AgentInterface
import io.jenkins.common.Colors

class NodeAgent extends AgentInterface {
  NodeAgent(script) {
    super(script)
  }



@Override
  void build(Map options = [:]) {
    script.node {
      script.echo "${Colors.CYAN}🖥️ 使用 Node Agent 进行构建${Colors.RESET}"
      def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
      
      if (projectDir?.trim()) {
        script.dir(projectDir) {
          script.build_client.build(script.hook_funcs)
          if(script.env.PLATFORM == "kubernetes" && script.env.SKIP_BUILD_IMG != "true") {
            script.image_builer.buildImage()
          }
        }
      } else {
        script.build_client.build(script.hook_funcs)
      }
    }
  }

  @Override
  void buildImage(Map options = [:]) {
    // NOTE: 若此方法应支持 projectDir/moduleConfig，请传入参数
    script.node {
      script.echo "${Colors.CYAN}🖥️ 使用 Node Agent 构建镜像${Colors.RESET}"
      script.build_client.build(script.hook_funcs) // 这里的参数需根据上下文调整
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
