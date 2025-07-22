import io.jenkins.agent.DockerAgent
import io.jenkins.agent.KubernetesAgent
import io.jenkins.agent.NodeAgent

def scriptRef = null
def agentManager = [:]
def initialized = false

def init(script) {
  if (initialized && scriptRef != null) return this

  scriptRef = script
  initialized = true

  def dockerAgent = new DockerAgent(script)
  def kubernetesAgent = new KubernetesAgent(script)
  def nodeAgent = new NodeAgent(script)

  agentManager['docker'] = dockerAgent
  agentManager['kubernetes'] = kubernetesAgent
  agentManager['node'] = nodeAgent

  return this
}

def getAgent(String agentType) {
  if (!scriptRef) error "Manager 尚未初始化"

  def selected = (
    agentType in ['docker', 'container'] ? 'docker' :
    agentType in ['kubernetes', 'k8s'] ? 'kubernetes' :
    agentType in ['node', 'any', 'default'] ? 'node' :
    'node'
  )

  def agent = agentManager[selected]
  if (!agent) error "未找到 ${selected} agent"

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

// 暴露给 pipeline 调用
this.init = this.&init
this.getAgent = this.&getAgent
this.build = this.&build
this.buildImage = this.&buildImage
this.deploy = this.&deploy
this.getRecommendedAgent = this.&getRecommendedAgent
