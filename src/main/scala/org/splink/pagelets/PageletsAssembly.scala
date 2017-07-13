package org.splink.pagelets

import play.api.mvc.{ControllerComponents, InjectedController}

class InjectedPageletsAssembly extends InjectedController with PageletsAssembly

trait PageletsAssembly extends Pagelets
  with PageletActionsImpl
  with PageBuilderImpl
  with LeafBuilderImpl
  with ActionBuilderImpl
  with TreeToolsImpl
  with ResourceActionsImpl
  with ResourcesImpl
  with VisualizerImpl {
  protected def controllerComponents: ControllerComponents
}