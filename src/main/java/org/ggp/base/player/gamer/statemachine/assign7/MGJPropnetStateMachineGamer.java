package org.ggp.base.player.gamer.statemachine.assign7;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
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
public final class MGJPropnetStateMachineGamer extends SampleGamer
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

	// Class to represent Node in search tree
	public class Node {
		List<Move> move = null;
		// Parent node of the current node
		public Node parent = null;
		// Array of all of the child nodes of the current node
		public ArrayList<Node> children = new ArrayList<Node>();
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
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		List<Gdl> rules = getMatch().getGame().getRules();
		try {
			PropNet propNet = OptimizingPropNetFactory.create(rules);
			System.out.println("Propnet size is " + propNet.getSize());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// start time
		long start = System.currentTimeMillis();

		// vars for role and state
		StateMachine machine = getStateMachine();
		Role role = getRole();
		List<Role> roles = machine.findRoles();
		int roleIdx = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = machine.findLegals(role, currentState);

		// if noop or only one possible move return immediately
		if (moves.size() == 1) return moves.get(0);

		num_depth_charges = 0;
		est_utility = 0;

		Node root = new Node(null, null, getCurrentState(), true);
		// Use Monte Carlo Tree Search to determine the best possible next move
		Move selection = bestMove(root, role, start, timeout, roleIdx, machine);

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
	private Move bestMove(Node root, Role role, long start, long timeout, int roleIdx, StateMachine machine) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		while (timeout - System.currentTimeMillis() >= time_lim) {
			Node selNode = select(root, machine);
			int score = 0;
			if (machine.findTerminalp(selNode.currentState)) {
				score = machine.findReward(role, selNode.currentState);
			} else {
				expand(selNode, role, machine);
				score = montecarlo(role, selNode, timeout, machine);
			}
			backpropagate(selNode, score);
		}

		// Parse through moves, find one with highest amount of utility, select it
		Node bestMove = root.children.get(0);
		double bestUtility =  root.children.get(0).utility;
		for (Node child : root.children) {
			if (machine.findTerminalp(child.currentState)) {
				if (machine.findReward(role, child.currentState) == 100) {
					est_utility = child.utility;
					return child.move.get(roleIdx);
				}
			}
			if (child.utility >= bestUtility) {
				bestUtility = child.utility;
				bestMove = child;
			}
		}
		est_utility = bestMove.utility;
		return bestMove.move.get(roleIdx);
	}

	private Node select(Node node, StateMachine machine) {
		if (machine.findTerminalp(node.currentState)) {
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
			return select(result, machine);
		}
	}

	private int selectfn(Node node) {
		return (int) (node.utility / node.visits + Math.sqrt(2 * Math.log(node.parent.visits) / node.visits));
	}

	private void expand(Node node, Role role, StateMachine machine) throws MoveDefinitionException, TransitionDefinitionException {
		List<Move> actions = machine.findLegals(role, node.currentState);
		for (Move action : actions) {
			List<List<Move>> allJointActions = machine.getLegalJointMoves(node.currentState, role, action);
			for (List<Move> jointActions : allJointActions) {
				MachineState newState = machine.findNext(jointActions, node.currentState);
				Node newnode = new Node(node, jointActions, newState, false);
				node.children.add(newnode);
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
	private int montecarlo(Role role, Node curr_node, long timeout, StateMachine machine) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int total = 0;
		for (int i = 0; i < count; i++) {
			total = total + depthcharge(role, curr_node, timeout, machine);
			num_depth_charges += 1;
		}
		return total / count;
	}

	/*
	 * Performs a depth charge by searching for a terminal state
	 */
	private int depthcharge(Role role, Node curr_node, long timeout, StateMachine m) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Random random = new Random();
		MachineState curr_state = curr_node.currentState;
		while (!m.findTerminalp(curr_state)) {
			if (timeout - System.currentTimeMillis() < absolute_lim) return 0;
			List<List<Move>> moves = m.getLegalJointMoves(curr_state);
			curr_state = m.getNextState(curr_state, moves.get(random.nextInt(moves.size())));
		}
		return m.findReward(role,  curr_state);
	}


}