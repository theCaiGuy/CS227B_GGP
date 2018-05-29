package org.ggp.base.player.gamer.statemachine.assign7;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

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
	private MGJPropNetStateMachine propNetMachine;

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
		List<Role> roles = propNetMachine.findRoles();
		int roleIdx = roles.indexOf(role);
		MachineState currentState = getCurrentState();

		// get the list of all possible moves
		List<Move> moves = propNetMachine.getLegalMoves(currentState, role);

		// if noop or only one possible move return immediately
		if (moves.size() == 1) return moves.get(0);

		num_depth_charges = 0;
		est_utility = 0;

		Node root = new Node(null, null, getCurrentState(), true);
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

	private Node select(Node node) {
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
			List<List<Move>> allJointActions = propNetMachine.getLegalJointMoves(node.currentState, role, action);
			for (List<Move> jointActions : allJointActions) {
				MachineState newState = propNetMachine.findNext(jointActions, node.currentState);
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

@SuppressWarnings("unused")
public class MGJPropnetStateMachineGamer extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            /*
            Set<Component> components = propNet.getComponents();
            for (Component c : components) {
            	if (c instanceof And) {
            		System.out.println(c);
            	} else {
            		System.out.println("Prop");
            	}
            }
            */
            ordering = getOrdering();
            System.out.println(propNet.getSize());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
        markBases(state);
        return propmarkp(propNet.getTerminalProposition());
//        return propNet.getTerminalProposition().getValue();
    }

    private void markBases(MachineState state) {
	    	Set<GdlSentence> stateBases = state.getContents();
	    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
	    	for (GdlSentence base : baseProps.keySet()) {
	    		if (stateBases.contains(base)) {
	    			baseProps.get(base).setValue(true);
	    		} else {
	    			baseProps.get(base).setValue(false);
	    		}
	    	}
    }

    private void markActions(List<Move> moves) {
	    	List<GdlSentence> does = toDoes(moves);
	    	Map<GdlSentence, Proposition> inputProps = propNet.getInputPropositions();
	    	for (GdlSentence input : inputProps.keySet()) {
	    		if (does.contains(input)) {
	    			inputProps.get(input).setValue(true);
	    		} else {
	    			inputProps.get(input).setValue(false);
	    		}
	    	}
    }

    private void clearPropNet() {
	    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
	    	for (GdlSentence base : baseProps.keySet()) {
	    		baseProps.get(base).setValue(false);
	    	}
    }

    private boolean propmarkp(Component c) {
	    	// check proposition versus transition
	    	if (c instanceof Proposition) {
	    		Proposition p = (Proposition) c;
	    		// input/base props return mark, otherwise return view prop source
	    		if (propNet.getBasePropositions().containsKey(p.getName()) ||
	    			propNet.getInputPropositions().containsKey(p.getName())) {
	    			return p.getValue();
	    		} else {
	    			try {
	    				return propmarkp(p.getSingleInput());
	    			} catch (Exception e) {
	    				return p.getValue();
	    			}
	    		}
	    	} else {
	    		if (c instanceof And) {
	    			return propmarkconjunction(c);
	    		} else if (c instanceof Not) {
	    			return propmarknegation(c);
	    		} else if (c instanceof Or) {
	    			return propmarkdisjunction(c);
	    		}
	    	}
	    	return false;
    }

    private boolean propmarknegation(Component c) {
	    	return !propmarkp(c.getSingleInput());
    }

    private boolean propmarkconjunction(Component c) {
	    	Set<Component> sources = c.getInputs();
	    	for (Component src : sources) {
	    		if (!propmarkp(src)) {
	    			return false;
	    		}
	    	}
	    	return true;
    }

    private boolean propmarkdisjunction(Component c) {
	    	Set<Component> sources = c.getInputs();
	    	for (Component src : sources) {
	    		if (propmarkp(src)) {
	    			return true;
	    		}
	    	}
	    	return false;
    }

    private List<Proposition> proplegals(Role role, MachineState state) {
	    	markBases(state);
	    	Set<Proposition> legals = propNet.getLegalPropositions().get(role);
	    	List<Proposition> actions = new ArrayList<Proposition>();
	    	for (Proposition l : legals) {
	    		if (propmarkp(l)) {
	    			actions.add(l);
	    		}
	    	}
	    	return actions;
    }

    private Set<GdlSentence> propnext(List<Move> move, MachineState state) {
	    	markActions(move);
	    	markBases(state);
	    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
	    	Set<GdlSentence> nexts = new HashSet<GdlSentence>();
	    	for (Entry <GdlSentence, Proposition> base_pair : bases.entrySet()) {
//	    		if (propmarkp(bases.get(base))) {
	    		if (propmarkp(base_pair.getValue().getSingleInput().getSingleInput())) {
	    			nexts.add(base_pair.getKey());
	    		}
	    	}
	    	return nexts;
    }

    private Proposition propreward(Role role, MachineState state) {
	    	markBases(state);
	    	Set<Proposition> rewards = propNet.getGoalPropositions().get(role);
	    	for (Proposition r : rewards) {
	    		if (propmarkp(r)) {
	    			return r;
	    		}
	    	}
	    	return null;
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        return getGoalValue(propreward(role, state));
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
        // TODO: Compute the initial state.
        return null;
    }

    /**
     * Computes all possible actions for role.
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
        // TODO: Compute legal moves.
        return null;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
    		List<Move> legal_moves = new ArrayList<Move>();
    		List<Proposition> legal_props = proplegals(role, state);
    		for (Proposition prop : legal_props) {
    			legal_moves.add(getMoveFromProposition(prop));
    		}
        return legal_moves;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    		Set<GdlSentence> nexts = propnext(moves, state);
    		return new MachineState(nexts);
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // TODO: Compute the topological ordering.

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}