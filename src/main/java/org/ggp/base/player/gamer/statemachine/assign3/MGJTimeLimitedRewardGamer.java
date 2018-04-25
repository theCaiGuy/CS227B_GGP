package org.ggp.base.player.gamer.statemachine.assign3;

import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/*
 * Team: Michael Genesereth Junior
 * MGJTimeLimitedRewardGamer is our implementation of a time limited
 * gamer that uses a reward heuristic for incomplete searches.
 * It partially searches the game tree from the current state to generate
 * minimum and maximum nodes using minScore and maxScore and uses
 * this to make an informed decision.
 */
public final class MGJTimeLimitedRewardGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a move generated via time-limited minimax.
	 */

	private long limit = 3250;

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// vars for role and state
		Role role = getRole();
		List<Role> roles = getStateMachine().findRoles();
		int role_index = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().findLegals(role, currentState);

		// Use minimax to determine the best possible next move
		Move selection = bestMove(role, currentState, moves, role_index, start, timeout);

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

	/*
	 * This function is called by stateMachineSelectMove. Given
	 * a role, state, list of potential actions to choose from in the given
	 * state, and index of the active role in the roles array,
	 * it finds the moves of the opponents that returns the lowest possible score
	 * (thereby populating the minnodes).
	 */
	private Move bestMove(Role role, MachineState state, List<Move> actions, int role_index, long start, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Move chosenMove = actions.get(0);
		double score = 0;
		int max_level = 1;
		// loop through all actions and find the best score and return this
		while (timeout - System.currentTimeMillis() >= limit) {
			for (int i = 0; i < actions.size(); i++) {
				double result = minScore(role, actions.get(i), state, role_index, start, timeout, 0, max_level);
				if (result > score) {
					score = result;
					chosenMove = actions.get(i);
				}
			}
			max_level += 1;
		}
		return chosenMove;
	}

	/*
	 * This function is called by bestMove and maxScore. Given a role,
	 * action chosen, state, and index of the active role
	 * in the roles array, calculates the minimum score out
	 * of all possible joint actions conducted by the opponents.
	 */
	private double minScore(Role role, Move move, MachineState state, int role_index, long start, long timeout, int curr_level, int max_level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		double score = 100;
		List<List<Move>> allJointActions = getStateMachine().getLegalJointMoves(state, role, move);
		// go through all possible combinations of actions for opponents and return worst outcome
		for (int i = 0; i < allJointActions.size(); i++) {
			MachineState updatedState = getStateMachine().findNext(allJointActions.get(i), state);
			double result = maxScore(role, updatedState, role_index, start, timeout, curr_level, max_level);
			if (result < score) {
				score = result;
			}
		}
		return score;
	}

	/*
	 * This function is called by minScore. Given a role,
	 * action chosen, state, and index of the active role
	 * in the roles array, finds the highest
	 * scoring move and returns its score.
	 */
	private double maxScore(Role role, MachineState state, int role_index, long start, long timeout, int curr_level, int max_level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// if in a terminal state or exceeds the time limit, return, otherwise recursively find all terminal results
		// note: the heuristic is integrated in this or statement because the helper function would be identical
		if (timeout - System.currentTimeMillis() < limit || getStateMachine().findTerminalp(state) || curr_level >= max_level) {
			return getStateMachine().findReward(role, state);
		}
		else {
			// find actions in this case and return the highest score found amongst them
			List<Move> actions = getStateMachine().findLegals(role, state);
			double score = 0;
			for (int i = 0; i < actions.size(); i++) {
				double result = minScore(role, actions.get(i), state, role_index, start, timeout, curr_level + 1, max_level);
				if (result > score) {
					score = result;
				}
			}
			return score;
		}
	}
}