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
public final class MGJFinalGamerMaximax extends SampleGamer
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
	private double opponent_est_utility = 0;
	private MGJPropNetStateMachine propNetMachine;
	private List<Role> roles;
	private int roleIdx;

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
		boolean isPlayerMove = false;

		//CAN BE CHANGED
		// Utility of the move
		public double utility = 0.0;
		public double opponent_utility = 0.0;
		// Number of visits for
		public double visits = 0.0;

		public Node(Node parent, Move move, MachineState currentState, boolean isRoot, boolean isPlayerMove) {
			this.parent = parent;
			this.move = move;
			this.currentState = currentState;
			this.isRoot = isRoot;
			this.isPlayerMove = isPlayerMove;
		}
	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		List<Gdl> rules = getMatch().getGame().getRules();
		propNetMachine = new MGJPropNetStateMachine();
		propNetMachine.initialize(rules);
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// vars for role and state
		Role role = getRole();
		roles = propNetMachine.findRoles();
		roleIdx = roles.indexOf(role);
		MachineState currentState = getCurrentState();

//		System.out.println(roles);
//		System.out.println(roleIdx);

		// get the list of all possible moves
		List<Move> moves = propNetMachine.getLegalMoves(currentState, role);

		// if noop or only one possible move return immediately
		if (moves.size() == 1) return moves.get(0);

		num_depth_charges = 0;
		est_utility = 0;
		opponent_est_utility = 0;

		Node root = new Node(null, null, getCurrentState(), true, false);
		// Use Monte Carlo Tree Search to determine the best possible next move
		Move selection = bestMove(root, role, start, timeout, roleIdx);

		// Print child moves, utilities, visits
		// Print joint moves, opponent utilites, visits
		for (Node child : root.children) {
			System.out.println("Child move: " + child.move);
			System.out.println("Child utility: " + child.utility/child.visits);
			System.out.println("Child opponent utility: " + child.opponent_utility/child.visits);
			System.out.println("Child visits: " + child.visits);
			for (Node grandchild : child.children) {
				System.out.println("Grandchild utility: " + grandchild.utility/grandchild.visits);
				System.out.println("Grandchild opponent utility: " + grandchild.opponent_utility/grandchild.visits);
				System.out.println("Grandchild visits: " + grandchild.visits);
			}
		}

		System.out.println("Estimated utility: " + est_utility);
		System.out.println("Estimated opponent utility: " + opponent_est_utility);
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
			double scores[] = {0, 0};
			if (propNetMachine.isTerminal(selNode.currentState)) {
				scores[0] = propNetMachine.getGoal(selNode.currentState, role);
				for (int i = 0; i < roles.size(); i++) {
					if (i == roleIdx) continue;
					int opponent_goal = propNetMachine.getGoal(selNode.currentState, roles.get(i));
					if (opponent_goal > scores[1]) scores[1] = opponent_goal;
				}
			} else {
				expand(selNode, role);
				scores = montecarlo(role, selNode, timeout);
			}
			backpropagate(selNode, scores[0], scores[1]);
		}

		// Parse through moves, find one with highest amount of utility, select it
		Node bestMove = root.children.get(0);
		double bestUtility =  root.children.get(0).utility;
		for (Node child : root.children) {
			if (child.utility >= bestUtility) {
				bestUtility = child.utility;
				bestMove = child;
			}
		}
		est_utility = bestMove.utility/bestMove.visits;
		opponent_est_utility = bestMove.opponent_utility/bestMove.visits;
		return bestMove.move;
	}

	private Node select(Node node) throws MoveDefinitionException {
		if (propNetMachine.isTerminal(node.currentState)) {
			return node;
		}
		if (node.visits == 0) {
			return node;
		}
		if (node.isPlayerMove && roles.size() > 1) {
			double max_opponent_score = 0.0;
			Node result = node.children.get(0);
			for (Node child : node.children) {
				if (child.visits == 0) return child;
				int child_score = selectfn(child.opponent_utility, child.visits, child.parent.visits);
				if (child_score > max_opponent_score) {
					max_opponent_score = child_score;
					result = child;
				}
			}
			return select(result);
		} else {
			int score = 0;
			Node result = node.children.get(0);
			for (Node child : node.children) {
				if (child.visits == 0) {
					return child;
				}
				int child_score = selectfn(child.utility, child.visits, child.parent.visits);
				if (child_score > score) {
					score = child_score;
					result = child;
				}
			}
			return select(result);
		}
	}

	private int selectfn(double utility, double visits, double parent_visits) {
		return (int) (utility / visits + Math.sqrt(2 * Math.log(parent_visits) / visits));
	}

	private void expand(Node node, Role role) throws MoveDefinitionException, TransitionDefinitionException {
		if (node.isPlayerMove && roles.size() > 1) {
			List<List<Move>> allJointActions = propNetMachine.getLegalJointMoves(node.currentState, role, node.move);
			for (List<Move> jointActions : allJointActions) {
				MachineState newState = propNetMachine.findNext(jointActions, node.currentState);
				Node new_opponent_node = new Node(node, null, newState, false, false);
				node.children.add(new_opponent_node);
			}
		} else {
			List<Move> actions = propNetMachine.getLegalMoves(node.currentState, role);
			for (Move action : actions) {
				Node player_node = new Node(node, action, node.currentState, false, true);
				if (propNetMachine.findRoles().size() == 1) {
					List<Move> moves = new ArrayList<Move>();
					moves.add(action);
					player_node.currentState = propNetMachine.findNext(moves, node.currentState);
				}
				node.children.add(player_node);
			}
		}
	}

	/*
	 * Backpropogates a found score to parent nodes until the root is reached
	 */
	private void backpropagate(Node node, double score, double opponent_score) {
		Node curr_node = node;
		node.visits += 1;
		node.utility = node.utility + score;
		while (curr_node.parent != null) {
			curr_node = curr_node.parent;
			curr_node.visits += 1;
			curr_node.utility += score;
			curr_node.opponent_utility += opponent_score;
		}
	}

	/*
	 * Manages depth charges for a monte carlo search
	 */
	private double[] montecarlo(Role role, Node curr_node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		double averages[] = {0,0};
		for (int i = 0; i < count; i++) {
			double scores[] = depthcharge(role, curr_node, timeout);
			num_depth_charges += 1;
			averages[0] += scores[0];
			averages[1] += scores[1];
		}
		averages[0] = averages[0] / count;
		averages[1] = averages[1] / count;
//		System.out.println("Average player depthcharge: " + averages[0]);
//		System.out.println("Average opponent depthcharge: " + averages[1]);
		return averages;
	}

	/*
	 * Performs a depth charge by searching for a terminal state
	 */
	private double[] depthcharge(Role role, Node curr_node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Random random = new Random();
		MachineState curr_state = curr_node.currentState;
		if (curr_node.isPlayerMove && roles.size() > 1) {
			List<List<Move>> moves = propNetMachine.getLegalJointMoves(curr_node.currentState, role, curr_node.move);
			curr_state = propNetMachine.getNextState(curr_node.currentState, moves.get(random.nextInt(moves.size())));
		}
		while (!propNetMachine.isTerminal(curr_state)) {
			if (timeout - System.currentTimeMillis() < absolute_lim) {
				double scores[] = {0, 0};
				return scores;
			}
			List<List<Move>> moves = propNetMachine.getLegalJointMoves(curr_state);
			curr_state = propNetMachine.getNextState(curr_state, moves.get(random.nextInt(moves.size())));
		}
		double scores[] = {0, 0};
		if (roles.size() == 1) {
			scores[0] = propNetMachine.getGoal(curr_state, role);
			return scores;
		}
		int max_opponent_goal = 0;
		scores[0] = propNetMachine.getGoal(curr_state, role);
		for (int i = 0; i < roles.size(); i++) {
			if (i == roleIdx) {
				continue;
			} else {
				int opponent_goal = propNetMachine.getGoal(curr_state, roles.get(i));
				if (opponent_goal > max_opponent_goal) max_opponent_goal = opponent_goal;
			}
		}
		scores[1] = max_opponent_goal;
		return scores;
	}


}