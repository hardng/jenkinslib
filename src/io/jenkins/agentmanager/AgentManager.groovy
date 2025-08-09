package io.jenkins.agentmanager

import io.jenkins.agent.DockerAgent
import io.jenkins.agent.KubernetesAgent
import io.jenkins.agent.NodeAgent

class AgentManager implements Serializable {
  def script
  def agentMap = [:]

  private AgentManager(script) {
    this.script = script
  }

  static def init(script) {
    def manager = new AgentManager(script)
    manager.agentMap['docker'] = new DockerAgent(script)
    manager.agentMap['kubernetes'] = new KubernetesAgent(script)
    manager.agentMap['node'] = new NodeAgent(script)
    return manager
  }

  def getAgent(String agentType) {
    def selected = (
      agentType in ['docker', 'container'] ? 'docker' :
      agentType in ['kubernetes', 'k8s'] ? 'kubernetes' :
      agentType in ['node', 'any', 'default'] ? 'node' :
      'node'
    )

    def agent = agentMap[selected]
    if (!agent) {
      script.error "未找到 ${selected} agent"
    }
    return agent
  }

  def build(String agentType) {
    def agent = getAgent(agentType)

    def imageMap = env.IMAGES?.trim() ? readJSON(text: env.IMAGES) : [:]
    def insideArgsMap = env.INSIDE_ARGS?.trim() ? readJSON(text: env.INSIDE_ARGS) : [:]

    def buildOptions = [
      image      : imageMap.get("build"),
      insideArgs : insideArgsMap.get("build")
    ]
    agent.build(buildOptions)
  }

  def buildImage(String agentType, Map options = [:]) {
    def agent = getAgent(agentType)
    agent.buildImage(options)
  }

  def deploy(String agentType) {
    def agent = getAgent(agentType)

    def imageMap = env.IMAGES?.trim() ? readJSON(text: env.IMAGES) : [:]
    def insideArgsMap = env.INSIDE_ARGS?.trim() ? readJSON(text: env.INSIDE_ARGS) : [:]

    def buildOptions = [
        image      : imageMap.get("deploy"),
        insideArgs : insideArgsMap.get("deploy")
    ]

    agent.deploy(buildOptions)
  }

  def getRecommendedAgent(String programming, String platform) {
    if (platform?.toLowerCase() == "kubernetes") return "kubernetes"
    if (platform?.toLowerCase() == "docker") return "docker"

    switch(programming?.toLowerCase()) {
      case 'java':
      case 'maven':
      case 'rust':
        return "kubernetes"
      case 'docker':
        return "docker"
      default:
        return "any"
    }
  }

}
