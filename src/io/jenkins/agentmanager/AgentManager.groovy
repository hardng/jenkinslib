package io.jenkins.agentmanager

import io.jenkins.agent.DockerAgent
import io.jenkins.agent.KubernetesAgent
import io.jenkins.agent.NodeAgent

class AgentManager implements Serializable {
  def script
  def agentMap = [:]

  AgentManager(script) {
    this.script = script
    this.initAgents()
  }

  private void initAgents() {
    agentMap['docker'] = new DockerAgent(script)
    agentMap['kubernetes'] = new KubernetesAgent(script)
    agentMap['node'] = new NodeAgent(script)
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
    agent.build()
  }

  def buildImage(String agentType) {
    def agent = getAgent(agentType)
    agent.buildImage()
  }

  def deploy(String agentType) {
    def agent = getAgent(agentType)
    agent.deploy()
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
