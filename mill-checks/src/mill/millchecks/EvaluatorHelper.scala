package mill.millchecks

import mill.api.Evaluator

/** Helper to access private[mill] fields of `Evaluator` */
case class EvaluatorHelper(evaluator: Evaluator) {
  def rootModule = evaluator.rootModule
}
