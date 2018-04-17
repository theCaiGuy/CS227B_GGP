package org.ggp.base.player.gamer.statemachine.assign2;

import java.util.ArrayList;
import java.util.List;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.GdlPool;
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
public final class MGJCompulsiveDeliberationGamer extends SampleGamer
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
	 * Fills a list of actions for each role to obtain the next state of the game
	 */
	private List<Move> fill_action_list(Move action, int role_index, int num_roles) {
		List<Move> action_list = new ArrayList<Move>();
		for (int i = 0; i < num_roles; i++) {
			if (i == role_index) {
				action_list.add(action);
			} else {
				action_list.add(new Move(GdlPool.getConstant("NOOP")));
			}
		}
		return action_list;
	}

	/*
	 * This function is called by stateMachineSelectMove. Given
	 * a role, state, and list of potential actions to choose from
	 * in the current state, it finds the move that returns the highest
	 * potential score by completely exploring the game tree and returns
	 * what this move is.
	 */
	private Move bestMove(Role role, MachineState state, List<Move> actions, int role_index, int num_roles) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Move chosenMove = actions.get(0);
		int score = 0;
		// loop through all actions and find the best score and return this
		for (int i = 0; i < actions.size(); i++) {
			List<Move> next_actions = fill_action_list(actions.get(i), role_index, num_roles);
			int result = maxScore(role, getStateMachine().findNext(next_actions, state), role_index, num_roles);
			if (result == 100) {
				return actions.get(i);
			} else if (result > score) {
				score = result;
				chosenMove = actions.get(i);
			}
		}
		return chosenMove;
	}

	/*
	 * This function is called by bestMove and itself. Given
	 * a role and a state, it calculates the highest scoring move
	 * and returns this score.
	 */
	private int maxScore(Role role, MachineState state, int role_index, int num_roles) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// if in a terminal state, return, otherwise recursively find all terminal results
		if (getStateMachine().findTerminalp(state)) {
			return getStateMachine().findReward(role, state);
		} else {
			// find actions in this case and return the highest score found amongst them
			List<Move> actions = getStateMachine().findLegals(role, state);
			int score = 0;
			for (int i = 0; i < actions.size(); i++) {
				List<Move> next_actions = fill_action_list(actions.get(i), role_index, num_roles);
				int result = maxScore(role, getStateMachine().findNext(next_actions, state), role_index, num_roles);
				if (result > score) {
					score = result;
				}
			}
			return score;
		}
	}
}