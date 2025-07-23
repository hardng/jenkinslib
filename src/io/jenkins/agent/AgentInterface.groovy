package io.jenkins.agent

abstract class AgentInterface implements Serializable {
  def script

  AgentInterface(script) {
    this.script = script
  }

  abstract void build(Map hookFuncs = [:])
  abstract void buildImage()
  abstract void deploy()
}
