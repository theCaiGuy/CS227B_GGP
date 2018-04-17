package org.ggp.base.player.gamer.statemachine.assign2;

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
 * MGJRandomGamer is our implementation of a compulsive deliberation gamer.
 * It fully searches the game tree from the current state to choose the move
 * that will maximize its score or get it to 100 (the maximum score) and
 * returns this as the move.
 */
public final class MGJMinimaxGamer extends SampleGamer
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

		// vars for role and state
		Role role = getRole();
		List<Role> roles = getStateMachine().findRoles();
		int role_index = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().findLegals(role, currentState);

		// Use compulsive deliberation to determine the best possible next move
		Move selection = bestMove(role, currentState, moves, role_index, roles.size());

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
	 * state, index of the active role in the roles array, and total number of roles,
	 * it finds the moves of the opponents that returns the lowest possible score
	 * (thereby populating the minnodes).
	 */
	private Move bestMove(Role role, MachineState state, List<Move> actions, int role_index, int num_roles) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Move chosenMove = actions.get(0);
		int score = 0;
		// loop through all actions and find the best score and return this
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(role, actions.get(i), state, role_index);
			if (result > score) {
				score = result;
				chosenMove = actions.get(i);
			}
		}
		return chosenMove;
	}

	/*
	 * This function is called by bestMove. Given a role,
	 * action chosen, state, index of the active role in the
	 * roles array, and total number of roles, calculates
	 * the minimum score out of all possible joint actions
	 * conducted by the opponents.
	 */
	private int minScore(Role role, Move move, MachineState state, int role_index) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int score = 100;
		List<List<Move>> allJointActions = getStateMachine().getLegalJointMoves(state, role, move);
		// go through all possible combinations of actions for opponents and return worst outcome
		for (int i = 0; i < allJointActions.size(); i++) {
			MachineState updatedState = getStateMachine().findNext(allJointActions.get(i), state);
			int result = maxScore(role, updatedState, role_index);
			if (result < score) {
				score = result;
			}
		}
		return score;
	}

	/*
	 * This function is called by minScore. Given a role,
	 * action chosen, state, index of the active role in the
	 * roles array, and total number of roles, finds the highest
	 * scoring move and returns its score.
	 */
	private int maxScore(Role role, MachineState state, int role_index) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// if in a terminal state, return, otherwise recursively find all terminal results
		if (getStateMachine().findTerminalp(state)) {
			return getStateMachine().findReward(role, state);
		} else {
			// find actions in this case and return the highest score found amongst them
			List<Move> actions = getStateMachine().findLegals(role, state);
			int score = 0;
			for (int i = 0; i < actions.size(); i++) {
				int result = minScore(role, actions.get(i), state, role_index);
				if (result > score) {
					score = result;
				}
			}
			return score;
		}
	}
}