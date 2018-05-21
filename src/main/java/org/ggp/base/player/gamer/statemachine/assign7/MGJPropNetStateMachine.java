package org.ggp.base.player.gamer.statemachine.assign7;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class MGJPropNetStateMachine extends StateMachine {
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
        Proposition terminalProp = propNet.getTerminalProposition();
        return false;
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
    			return propmarkp(p.getSingleInput());
    		}
    	} else {
    		if (c instanceof And) {
    			return propmarkconjunction(c);
    		} else if (c instanceof Not) {
    			return propmarknegation(c);
    		} else if (c instanceof Or) {
    			return propmarkconjunction(c);
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

    private List<Proposition> propnext(List<Move> move, MachineState state) {
    	markActions(move);
    	markBases(state);
    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
    	List<Proposition> nexts = new ArrayList<Proposition>();
    	for (int i = 0; i < bases.size(); i++) {
    		if (propmarkp(bases.get(i).getSingleInput().getSingleInput())) {
    			nexts.add(bases.get(i));
    		}
    	}
    	return nexts;
    }

    private GdlSentence propreward(Role role, MachineState state) {
    	markBases(state);
    	Set<Proposition> rewards = propNet.getGoalPropositions().get(role);
    	for (Proposition r : rewards) {
    		if (propmarkp(r)) {
    			return r.getName();
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
        // TODO: Compute the goal for role in state.
        return -1;
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
        // TODO: Compute legal moves.
        return null;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
        // TODO: Compute the next state.
        return null;
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