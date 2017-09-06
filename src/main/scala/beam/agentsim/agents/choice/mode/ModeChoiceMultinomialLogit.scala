package beam.agentsim.agents.choice.mode

import java.util.Random

import beam.agentsim.agents.modalBehaviors.ModeChoiceCalculator
import beam.router.Modes.BeamMode
import beam.router.Modes.BeamMode.{CAR, RIDEHAIL, TRANSIT}
import beam.router.RoutingModel.EmbodiedBeamTrip
import beam.sim.BeamServices

/**
  * BEAM
  */
class ModeChoiceMultinomialLogit(val beamServices: BeamServices) extends ModeChoiceCalculator {

  override def apply(alternatives: Vector[EmbodiedBeamTrip]) = {
    var containsDriveAlt = -1

    var altModesAndTimes: Vector[(BeamMode, Double)] = for (i <- alternatives.indices.toVector) yield {
      val alt = alternatives(i)
      val altMode = if (alt.legs.size == 1) {
        alt.legs.head.beamLeg.mode
      } else {
        if (alt.legs.head.beamLeg.mode.equals(CAR)) {
          containsDriveAlt = i
          CAR
        } else {
          TRANSIT
        }
      }
      val travelTime = (for (leg <- alt.legs) yield leg.beamLeg.duration).foldLeft(0.0) {
        _ + _
      }
      (altMode, travelTime)
    }

    val altUtilities = for (alt <- altModesAndTimes) yield altUtility(alt._1, alt._2)
    val sumExpUtilities = altUtilities.foldLeft(0.0)(_ + math.exp(_))
    val altProbabilities = for (util <- altUtilities) yield math.exp(util) / sumExpUtilities
    val cumulativeAltProbabilities = altProbabilities.scanLeft(0.0)(_ + _)
    //TODO replace with RNG in services
    val randDraw = (new Random()).nextDouble()
    val chosenIndex = for (i <- 1 until cumulativeAltProbabilities.length if randDraw < cumulativeAltProbabilities(i)) yield i - 1
    if (chosenIndex.size > 0) {
      alternatives(chosenIndex.head)
    } else {
      EmbodiedBeamTrip.empty
    }
  }
  private def altUtility(mode: BeamMode, travelTime: Double): Double = {
    val intercept = if(mode.equals(CAR)){ -3.0 }else{ if(mode.equals(RIDEHAIL)){ -5.0}else{0.0} }
    intercept + -0.001 * travelTime
  }


}