package io.jenkins.agent

abstract class AgentInterface implements Serializable {
  def script

  AgentInterface(script) {
    this.script = script
  }

  abstract void build(Map options = [:])
  abstract void buildImage(Map options = [:])
  abstract void deploy(Map options = [:])
}
