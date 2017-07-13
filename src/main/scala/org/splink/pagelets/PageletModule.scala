package org.splink.pagelets

import play.api.inject.Module
import play.api.{Configuration, Environment}

class PageletModule extends Module {
  def bindings(environment: Environment, configuration: Configuration) =
    Seq(bind[Pagelets].to[InjectedPageletsAssembly])
}