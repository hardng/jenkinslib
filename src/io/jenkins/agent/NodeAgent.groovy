package io.jenkins.agent

import io.jenkins.agent.AgentInterface

class NodeAgent extends AgentInterface {
  NodeAgent(script) {
    super(script)
  }

  @Override
  void build(Map hookFuncs = [:]) {
    script.node {
      script.echo "${script.vars.green}🖥️ 使用 Node Agent 进行构建${script.vars.reset}"
      def projectDir = "${script.env.ROOT_WORKSPACE}/${script.env.MAIN_PROJECT}"
      
      if (projectDir?.trim()) {
        script.dir(projectDir) {
          script.build_client.build(script.hook_funcs)

          script.image_builer.buildImage()
        }
      } else {
        script.build_client.build(script.hook_funcs)
      }
    }
  }

  @Override
  void buildImage() {
    // NOTE: 若此方法应支持 projectDir/moduleConfig，请传入参数
    script.node {
      script.echo "${script.vars.green}🖥️ 使用 Node Agent 构建镜像${script.vars.reset}"
      script.build_client.build(script.hook_funcs) // 这里的参数需根据上下文调整
    }
  }

  @Override
  void deploy() {
    script.common.withAgentWorkspace(script) {
      script.node {
        script.echo "${script.vars.cyan}🖥️ 使用 Node Agent 部署${script.vars.reset}"
        script.deploy_client.mainDeployStage()
      }
    }
  }
}
