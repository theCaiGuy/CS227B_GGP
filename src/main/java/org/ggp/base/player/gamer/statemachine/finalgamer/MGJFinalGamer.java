package org.ggp.base.player.gamer.statemachine.finalgamer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
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
public final class MGJFinalGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a move generated via Monte Carlo Tree Search
	 */


	private long time_lim = 3000; // time limit
	private long absolute_lim = 2500;
	private int count = 5; //num depth charges
	private int num_depth_charges = 0;
	private double est_utility = 0;
	private MGJPropNetStateMachine propNetMachine;

	// Class to represent Node in search tree
	public class Node {
		Move move = null;
		// Parent node of the current node
		public Node parent = null;
		// Array of all of the child nodes of the current node
		public ArrayList<Node> children = new ArrayList<Node>();
		// Represents the current state of the machine at that node (used to find current state of next nodes)
		MachineState currentState = null;
		// Is the root of the tree or not
		boolean isRoot = false;
		boolean player_move = false;

		//CAN BE CHANGED
		// Utility of the move
		public double utility = 0.0;
		// Number of visits for
		public double visits = 0.0;

		public Node(Node parent, Move move, MachineState currentState, boolean isRoot, boolean player_move) {
			this.parent = parent;
			this.move = move;
			this.currentState = currentState;
			this.isRoot = isRoot;
			this.player_move = player_move;
		}
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		List<Gdl> rules = getMatch().getGame().getRules();
		propNetMachine = new MGJPropNetStateMachine();
		propNetMachine.initialize(rules);
		// For single-player games, factor propnet for multiple games
		if (propNetMachine.findRoles().size() == 1) {
			propNetMachine.pruneMultipleGames();
		}
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// vars for role and state
		Role role = getRole();
		List<Role> roles = propNetMachine.findRoles();
		int roleIdx = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = propNetMachine.getLegalMoves(currentState, role);

		// if noop or only one possible move return immediately
		if (moves.size() == 1) return moves.get(0);

		num_depth_charges = 0;
		est_utility = 0;

		Node root = new Node(null, null, getCurrentState(), true, false);
		// Use Monte Carlo Tree Search to determine the best possible next move
		Move selection = bestMove(root, role, start, timeout, roleIdx);

		System.out.println("Estimated utility: " + est_utility);
		System.out.println("Number of depth charges: " + num_depth_charges);

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
	private Move bestMove(Node root, Role role, long start, long timeout, int roleIdx) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		while (timeout - System.currentTimeMillis() >= time_lim) {
			Node selNode = select(root);
			int score = 0;
			if (propNetMachine.isTerminal(selNode.currentState)) {
				score = propNetMachine.getGoal(selNode.currentState, role);
			} else {
				expand(selNode, role);
				score = montecarlo(role, selNode, timeout);
			}
			backpropagate(selNode, score);
		}

		// Parse through moves, find one with highest amount of utility, select it
		Node bestMove = root.children.get(0);
		double bestUtility =  root.children.get(0).utility;
		for (Node child : root.children) {
			if (propNetMachine.isTerminal(child.currentState)) {
				if (propNetMachine.getGoal(child.currentState, role) == 100) {
					est_utility = child.utility/child.visits;
					return child.move;
				}
			}
			if (child.utility >= bestUtility) {
				bestUtility = child.utility;
				bestMove = child;
			}
		}
		est_utility = bestMove.utility/bestMove.visits;
		return bestMove.move;
	}

	private Node select(Node node) throws MoveDefinitionException {
		if (propNetMachine.isTerminal(node.currentState)) {
			return node;
		}
		if (node.visits == 0) {
			return node;
		} else {
			int score = 0;
			Node result = node;
			for (Node child : node.children) {
				if (child.visits == 0) {
					return child;
				}
				int child_score = selectfn(child);
				if (child_score > score) {
					score = child_score;
					result = child;
				}
			}
			return select(result);
		}
	}

	private int selectfn(Node node) {
		return (int) (node.utility / node.visits + Math.sqrt(2 * Math.log(node.parent.visits) / node.visits));
	}

	private void expand(Node node, Role role) throws MoveDefinitionException, TransitionDefinitionException {
		List<Move> actions = propNetMachine.getLegalMoves(node.currentState, role);
		for (Move action : actions) {
//			Node new_player_node = new Node(node, action, node.currentState, false, true);
//			node.children.add(new_player_node);
			List<List<Move>> allJointActions = propNetMachine.getLegalJointMoves(node.currentState, role, action);
			for (List<Move> jointActions : allJointActions) {
				MachineState newState = propNetMachine.findNext(jointActions, node.currentState);
				Node new_opponent_node = new Node(node, action, newState, false, false);
				node.children.add(new_opponent_node);
			}
		}
	}

//	private void backpropagate(Node node, int score) {
//		node.visits++;
//		node.utility = node.utility + score;
//		if (node.parent != null) {
//			backpropagate(node.parent, score);
//		}
//	}

	/*
	 * Backpropogates a found score to parent nodes until the root is reached
	 */
	private void backpropagate(Node node, int score) {
		Node curr_node = node;
		node.visits += 1;
		node.utility = node.utility + score;
		while (curr_node.parent != null) {
			curr_node = curr_node.parent;
			curr_node.visits += 1;
			curr_node.utility += score;
		}
	}

	/*
	 * Manages depth charges for a monte carlo search
	 */
	private int montecarlo(Role role, Node curr_node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int total = 0;
		for (int i = 0; i < count; i++) {
			total = total + depthcharge(role, curr_node, timeout);
			num_depth_charges += 1;
		}
		return total / count;
	}

	/*
	 * Performs a depth charge by searching for a terminal state
	 */
	private int depthcharge(Role role, Node curr_node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Random random = new Random();
		MachineState curr_state = curr_node.currentState;
		while (!propNetMachine.isTerminal(curr_state)) {
			if (timeout - System.currentTimeMillis() < absolute_lim) return 0;
			List<List<Move>> moves = propNetMachine.getLegalJointMoves(curr_state);
			curr_state = propNetMachine.getNextState(curr_state, moves.get(random.nextInt(moves.size())));
		}
		return propNetMachine.getGoal(curr_state, role);
	}


}