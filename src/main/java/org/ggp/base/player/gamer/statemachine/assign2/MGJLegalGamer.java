package org.ggp.base.player.gamer.statemachine.assign2;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/*
 * Team: Michael Genesereth Junior
 * MGJLegalGamer is our implementation of a legal gamer.
 * It merely takes the first move it finds and chooses it
 * each time it is its turn to play.
 */
public final class MGJLegalGamer extends SampleGamer
{
	/*
	 * Team: Michael Genesereth Junior
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * the first move that it finds.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().findLegals(getRole(), getCurrentState());
		// pick the first move found
		Move selection = moves.get(0);

		/*
		 * get the final time after the move is chosen
		 * (this time must be less than timeout, or else
		 * the bot will not have played a move in the time
		 * allotted)
		 */
		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}
}