package org.splink.pagelets

import javax.inject.Inject
import play.api.mvc.{AbstractController, ControllerComponents, CookieHeaderEncoding, DefaultCookieHeaderEncoding}

class InjectedPageletsAssembly @Inject() (cc: ControllerComponents) extends AbstractController(cc) with PageletsAssembly

trait PageletsAssembly extends Pagelets
  with PageletActionsImpl
  with PageBuilderImpl
  with LeafBuilderImpl
  with PageletActionBuilderImpl
  with TreeToolsImpl
  with ResourceActionsImpl
  with ResourcesImpl
  with VisualizerImpl {
  protected def controllerComponents: ControllerComponents
}
