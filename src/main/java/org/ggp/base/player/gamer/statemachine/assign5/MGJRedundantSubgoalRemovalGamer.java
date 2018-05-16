package org.ggp.base.player.gamer.statemachine.assign5;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlRule;
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
public final class MGJRedundantSubgoalRemovalGamer extends SampleGamer
{
	/*
	 * This function is called whenever the gamer is queried
	 * for a move at the beginning of each round. It returns
	 * a move generated via Monte Carlo Tree Search
	 */


	private long time_lim = 3000; // time limit
	private long absolute_lim = 2500;
	private int count = 6; //num depth charges

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
		StateMachine newStateMachine = getInitialStateMachine();
		List<Gdl> rules = getMatch().getGame().getRules();
		for (int i = 0; i < rules.size(); i++) {
			Gdl rule = rules.get(i);
			if (timeout - System.currentTimeMillis() < 3300) {
				break;
			}
			if (rule instanceof GdlRule) {
				rules.set(i, prunesubgoals((GdlRule) rule));
			}
		}
		newStateMachine.initialize(rules);
		switchStateMachine(newStateMachine);
	}

	private Gdl prunesubgoals(GdlRule rule) {
		List<String> vl = new ArrayList<String>();
		varsexp(rule.getHead(), vl);
		// really uncertain about this function
		List<GdlLiteral> newBody = new ArrayList<GdlLiteral>();
		for (int i = 0; i < rule.getBody().size(); i++) {
			// the slicing and concat lines, v difficult, make sure these are good
			List<GdlLiteral> slFirstHalf = new ArrayList<GdlLiteral>(newBody);
			List<GdlLiteral> slSecondHalf = new ArrayList<GdlLiteral>(rule.getBody().subList(i + 1, newBody.size()));
			List<GdlLiteral> sl = new ArrayList<GdlLiteral>(slFirstHalf);
			sl.addAll(slSecondHalf);
			if (!pruneworthyp(sl, rule.get(i), vl)) {

			}
		}
		Gdl newRule = GdlPool.getRule(rule.getHead(), newBody);
		return newRule;
	}

	// all really weird because I don't know if sl is a list of literals or a single
	// literal, may need OH for this one
	private boolean pruneworthyp(List<GdlLiteral> sl, GdlLiteral p, List<String> vl) {
		List<String> vlCopy = vl;
		for (int i = 0; i < sl.size(); i++) {
			varsexp(sl.get(i), vlCopy);
		}
		Map<String, String> al = new HashMap<String, String>();
		for (int i = 0; i < vl.size(); i++) {
			al.put(vl.get(i), new StringBuilder("x").append(i).toString());
		}
		List<GdlLiteral> facts = new ArrayList<GdlLiteral>();
		for (int i = 0; i < sl.size(); i++) {
			facts.add(sublis(sl.get(i), al));
		}
		GdlLiteral goal = sublis(p, al);
		return compfindp(goal, facts, new ArrayList<String>()); //no idea what this is
	}

	// arguments below may be wrong, don't know what this does either
	private boolean compfindp(GdlLiteral goal, List<GdlLiteral> facts, ArrayList<String> res) {
		return true;
	}

	private GdlLiteral sublis(GdlLiteral exp, Map<String, String> bindings) {
		String newExp = exp.toString();
		String[] splitExp = newExp.split(" ");
		for (int i = 0; i < splitExp.length; i++) {
			if (bindings.containsKey(splitExp[i])) {
				splitExp[i] = bindings.get(splitExp[i]);
			}
		}
		newExp = String.join(" ", splitExp);
		return GdlPool.getConstant(newExp).toSentence(); //unsure here but this should work
	}

	private void varsexp(GdlLiteral ans, List<String> vl) {
		String ansStr = ans.toString();
		String[] splitStr = ansStr.split(" ");
		for (String token : splitStr) {
			if (token.charAt(0) == '?' && !vl.contains(token)) {
				vl.add(token);
			}
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

		Node root = new Node(null, null, getCurrentState(), true);
		// Use Monte Carlo Tree Search to determine the best possible next move
		Move selection = bestMove(root, role, start, timeout, roleIdx);

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
			if (getStateMachine().findTerminalp(selNode.currentState)) {
				score = getStateMachine().findReward(role, selNode.currentState);
			} else {
				expand(selNode, role);
				score = montecarlo(role, selNode, timeout);
			}
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
		if (getStateMachine().findTerminalp(node.currentState)) {
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
//		if (getStateMachine().findTerminalp(node.currentState)) {
//			return;
//		}
		List<Move> actions = getStateMachine().findLegals(role, node.currentState);
		for (Move action : actions) {
			List<List<Move>> allJointActions = getStateMachine().getLegalJointMoves(node.currentState, role, action);
			for (List<Move> jointActions : allJointActions) {
				MachineState newState = getStateMachine().findNext(jointActions, node.currentState);
				Node newnode = new Node(node, jointActions, newState, false);
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


	private int montecarlo(Role role, Node curr_node, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		int total = 0;
		for (int i = 0; i < count; i++) {
			total = total + depthcharge(role, curr_node, timeout, getStateMachine());
		}
		return total / count;
	}

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

//	/*
//	 * This function manages the depth charges extending
//	 * from a particular node. It adds up the resulting scores
//	 * found from each depth charge and returns the average
//	 * of those scores
//	 */
//	private int montecarlo(Role role, MachineState state, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
//		int total = 0;
//		for (int i = 0; i < count; i++) {
//			total = total + depthcharge(role, state, timeout, getStateMachine());
//		}
//		return total/count;
//	}
//
//	/*
//	 * This function performs a depth charge on the given
//	 * game tree. At each state, it randomly chooses one
//	 * move until a terminal state is reached, then returns
//	 * the reward received at said terminal state
//	 */
//	private int depthcharge(Role role, MachineState state, long timeout, StateMachine m) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
//		Random random = new Random();
//		MachineState current = state;
//		while (!m.findTerminalp(current)) {
//			List<List<Move>> moves = m.getLegalJointMoves(current);
//			current = m.getNextState(current, moves.get(random.nextInt(moves.size())));
//			if (timeout - System.currentTimeMillis() < absolute_lim) return 0;
//		}
//		return m.findReward(role, current);
//	}

}