package org.ggp.base.player.gamer.statemachine.assign2;

import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/*
 * Team: Michael Genesereth Junior
 * MGJRandomGamer is our implementation of a random gamer.
 * It merely takes the first move it finds and chooses it
 * each time it is its turn to play.
 */
public final class MGJRandomGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a random move from the moves it finds.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		// pick a random move out of the list of all possible moves
		Random rand = new Random();
		int moveIndex = rand.nextInt(moves.size());
		Move selection = moves.get(moveIndex);

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