package org.ggp.base.player.gamer.statemachine.assign4;

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
 * MGJMonteCarloGamer is our implementation of a Monte Carlo gamer.
 * It partially explores the game tree to a set depth, then uses depth charges
 * to simulate random game play in order to estimate the likelihood of any
 * particular move leading to a victory
 */
public final class MGJMonteCarloGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a move generated via depth-limited minimax.
	 */

	private int limit = 6; // level limit

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		int level = 0;

		// vars for role and state
		Role role = getRole();
		List<Role> roles = getStateMachine().findRoles();
		int role_index = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().findLegals(role, currentState);

		// Use minimax to determine the best possible next move
		Move selection = bestMove(role, currentState, moves, role_index, level);

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
	private Move bestMove(Role role, MachineState state, List<Move> actions, int role_index, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Move chosenMove = actions.get(0);
		int score = 0;
		// loop through all actions and find the best score and return this
		for (int i = 0; i < actions.size(); i++) {
			int result = minScore(role, actions.get(i), state, role_index, level);
			if (result > score) {
				score = result;
				chosenMove = actions.get(i);
			}
		}
		return chosenMove;
	}

	/*
	 * This function is called by bestMove and maxScore. Given a role,
	 * action chosen, state, and index of the active role
	 * in the roles array, calculates the minimum score out
	 * of all possible joint actions conducted by the opponents.
	 */
	private int minScore(Role role, Move move, MachineState state, int role_index, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int score = 100;
		List<List<Move>> allJointActions = getStateMachine().getLegalJointMoves(state, role, move);
		// go through all possible combinations of actions for opponents and return worst outcome
		for (int i = 0; i < allJointActions.size(); i++) {
			MachineState updatedState = getStateMachine().findNext(allJointActions.get(i), state);
			int result = maxScore(role, updatedState, role_index, level + 1);
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
	private int maxScore(Role role, MachineState state, int role_index, int level) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// if in a terminal state or exceeds the level limit, return, otherwise recursively find all terminal results
		if (getStateMachine().findTerminalp(state)) {
			return getStateMachine().findReward(role, state);
		} else if (level >= limit) return 0;
		else {
			// find actions in this case and return the highest score found amongst them
			List<Move> actions = getStateMachine().findLegals(role, state);
			int score = 0;
			for (int i = 0; i < actions.size(); i++) {
				int result = minScore(role, actions.get(i), state, role_index, level);
				if (result > score) {
					score = result;
				}
			}
			return score;
		}
	}

	/*
	 * This function manages the depth charges extending
	 * from a particular node. It adds up the resulting scores
	 * found from each depth charge and returns the average
	 * of those scores
	 */
	private int montecarlo(Role role, MachineState state, int count) {
		return 0;
	}

	/*
	 * This function performs a depth charge on the given
	 * game tree. At each state, it randomly chooses one
	 * move until a terminal state is reached, then returns
	 * the reward received at said terminal state
	 */
	private int depthcharge(Role role, MachineState state) {
		return 0;
	}






}