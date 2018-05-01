package org.ggp.base.player.gamer.statemachine.assign4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/*
 * Team: Michael Genesereth Junior
 * MGJMonteCarloTreeSearchGamer is our implementation of a Monte Carlo Tree gamer.
 * It partially explores the game tree to a set depth, then uses depth charges
 * to simulate random game play in order to estimate the likelihood of any
 * particular move leading to a victory
 */
public final class MGJMonteCarloTreeSearchBenGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a move generated via Monte Carlo Tree Search
	 */


	private long time_lim = 3000; // time limit
	private long absolute_lim = 2500;
	private int count = 6; //num depth charges
	ArrayList<Node> children = new ArrayList<Node>();
	private Node root = new Node(null, null, null, true);

	// Class to represent Node in search tree
	public class Node {
		List<Move> move = null;
		// Parent node of the current node
		public Node parent = null;
		// Array of all of the child nodes of the current node
		public ArrayList<Node> children = null;
		// Represents the current state of the machine at that node (used to find current state of next nodes)
		MachineState currentState = null;
		// Is the root of the tree or not
		boolean isRoot = false;

		//CAN BE CHANGED
		// Utility of the move
		public double utility = 0.0;
		// Number of visits for
		public double visits = 0.0;

		public Node(Node parent, List<Move> move, MachineState currentState, boolean isRoot) {
			this.parent = parent;
			this.move = move;
			this.currentState = currentState;
			this.isRoot = isRoot;
		}
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// vars for role and state
		Role role = getRole();
		List<Role> roles = getStateMachine().findRoles();
		int roleIdx = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = getStateMachine().findLegals(role, currentState);

		// if noop or only one possible move return immediately
		if (moves.size() == 1) return moves.get(0);

		List<List<Move>> jointMoves = getStateMachine().getLegalJointMoves(currentState);
        root.children = children;
		// Initializes root children
		ArrayList<Node> parentChildren = new ArrayList<Node>();
		for (List<Move> action : jointMoves) {
			Node newAction = new Node(root, action, getStateMachine().getNextState(getCurrentState(), action), false);
			parentChildren.add(newAction);
		}
		root.children = parentChildren;

		// Use Monte Carlo Tree Search to determine the best possible next move
		Move selection = bestMove(role, start, timeout, roleIdx);

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


	/* while still have time repeatedly update and search the tree
	 */
	private Move bestMove(Role role, long start, long timeout, int roleIdx) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		while (timeout - System.currentTimeMillis() >= time_lim) {
			Node selNode = select(root);
			expand(selNode, role);
			int score = montecarlo(role, selNode.currentState, timeout);
			backpropagate(selNode, score);
		}

		// Parse through moves, find one with highest amount of utility, select it
		Move bestMove = root.children.get(0).move.get(roleIdx);
		double bestUtility =  root.children.get(0).utility;
		for (Node child : root.children) {
			if (child.utility >= bestUtility) {
				bestUtility = child.utility;
				bestMove = child.move.get(roleIdx);
			}
		}
		return bestMove;
	}

	private Node select(Node node) {
		if (node.visits == 0 && !node.isRoot) {
			return node;
		} else {
			for (int i = 0; i < node.children.size(); i++) {
				if (node.children.get(i).visits == 0) {
					return node.children.get(i);
				}
			}
			int score = 0;
			Node result = node;
			for (int i = 0; i < node.children.size(); i++) {
				int newscore = selectfn(node.children.get(i));
				if (newscore > score) {
					score = newscore;
					result = node.children.get(i);
				}
			}
			return select(result);
		}
	}

	private int selectfn(Node node) {
		return (int) (node.utility / node.visits + Math.sqrt(2 * Math.log(node.parent.visits) / node.visits));
	}

	private void expand(Node node, Role role) throws MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().findTerminalp(node.currentState)) {
			return;
		}
		List<Move> actions = getStateMachine().findLegals(role, node.currentState);
		for (int i = 0; i < actions.size(); i++) {
			List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(node.currentState, role, actions.get(i));
			for (int j = 0; j < jointActions.size(); j++) {
				MachineState newState = getStateMachine().findNext(jointActions.get(j), node.currentState);
				Node newnode = new Node(node, jointActions.get(i), newState, false);
				node.children.add(newnode);
			}
		}
	}

	private void backpropagate(Node node, int score) {
		node.visits++;
		node.utility = node.utility + score;
		if (node.parent != null) {
			backpropagate(node.parent, score);
		}
	}

	/*
	 * This function manages the depth charges extending
	 * from a particular node. It adds up the resulting scores
	 * found from each depth charge and returns the average
	 * of those scores
	 */
	private int montecarlo(Role role, MachineState state, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int total = 0;
		for (int i = 0; i < count; i++) {
			total = total + depthcharge(role, state, timeout, getStateMachine());
		}
		return total/count;
	}

	/*
	 * This function performs a depth charge on the given
	 * game tree. At each state, it randomly chooses one
	 * move until a terminal state is reached, then returns
	 * the reward received at said terminal state
	 */
	private int depthcharge(Role role, MachineState state, long timeout, StateMachine m) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (timeout - System.currentTimeMillis() < absolute_lim) return 50;
		Random random = new Random();
		MachineState current = state;
		while (!m.findTerminalp(current)) {
			List<List<Move>> moves = m.getLegalJointMoves(current);
			current = m.getNextState(current, moves.get(random.nextInt(moves.size())));
		}
		return m.findReward(role, current);
	}

}