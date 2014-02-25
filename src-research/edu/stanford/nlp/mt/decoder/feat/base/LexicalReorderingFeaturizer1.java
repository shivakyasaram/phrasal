package edu.stanford.nlp.mt.decoder.feat.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.mt.base.ConcreteRule;
import edu.stanford.nlp.mt.base.CoverageSet;
import edu.stanford.nlp.mt.base.FeatureValue;
import edu.stanford.nlp.mt.base.Featurizable;
import edu.stanford.nlp.mt.base.LexicalReorderingTable;
import edu.stanford.nlp.mt.base.Sequence;
import edu.stanford.nlp.mt.base.SimpleSequence;
import edu.stanford.nlp.mt.base.IString;
import edu.stanford.nlp.mt.base.SourceClassMap;
import edu.stanford.nlp.mt.base.TargetClassMap;
import edu.stanford.nlp.mt.base.TokenUtils;
import edu.stanford.nlp.mt.decoder.feat.DerivationFeaturizer;
import edu.stanford.nlp.mt.decoder.feat.FeaturizerState;
import edu.stanford.nlp.mt.decoder.feat.sparse.SparseFeatureUtils;
import edu.stanford.nlp.mt.decoder.util.Derivation;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Generative and discriminative lexicalized reordering models.
 * 
 * @author danielcer
 * @author Spence Green
 */
public class LexicalReorderingFeaturizer1 extends
    DerivationFeaturizer<IString, String> {

  private static final int LEXICAL_FEATURE_CUTOFF = 50;
  private static final boolean DETAILED_DEBUG = false;
  private static final Sequence<IString> INITIAL_PHRASE = new SimpleSequence<IString>(
      TokenUtils.START_TOKEN);
  
  private static final String DISCRIMINATIVE_PREFIX = "Disc";
  private static final String FEATURE_PREFIX = "LexR:";
  public final String[] featureTags;
  public final LexicalReorderingTable mlrt;
  private List<LexicalReorderingTable.ReorderingTypes> discriminativeSet;
  private final boolean useAlignmentConstellations;
  private boolean useClasses;
  private final int countFeatureIndex;
  private SourceClassMap sourceMap;
  private TargetClassMap targetMap;
  
  private final boolean addDomainFeatures;
  private Map<Integer,Pair<String,Integer>> sourceIdInfoMap;

  private final int DEBUG_OPT = 1; // Thang Jan14: >0 print debugging message
  
  /**
   * Constructor for discriminative lexicalized reordering.
   */
  public LexicalReorderingFeaturizer1() {
    // by default include everything
    discriminativeSet = Generics.newArrayList(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    mlrt = null;
    featureTags = null;
    useAlignmentConstellations = false;
    useClasses = false;
    addDomainFeatures = false;
    countFeatureIndex = -1;
  }

  /**
   * Constructor for discriminative lexicalized reordering.
   * 
   * @param args
   */
  public LexicalReorderingFeaturizer1(String...args) {
    discriminativeSet = Generics.newArrayList(Arrays.asList(LexicalReorderingTable.ReorderingTypes.values()));
    Properties options = StringUtils.argsToProperties(args);
    this.useAlignmentConstellations = options.containsKey("conditionOnConstellations");
    this.countFeatureIndex = PropertiesUtils.getInt(options, "countFeatureIndex", -1);
    // Which reordering classes to extract
    if (options.containsKey("classes")) {
      String[] typeStrings = options.getProperty("classes").split("-");
      discriminativeSet = Generics.newArrayList();
      for (String type : typeStrings) {
        discriminativeSet.add(LexicalReorderingTable.ReorderingTypes.valueOf(type));
      }
    }
    // Use class-based feature representations
    this.useClasses = options.containsKey("useClasses");
    if (useClasses) {
      sourceMap = SourceClassMap.getInstance();
      targetMap = TargetClassMap.getInstance();

    }
    // Add domain-specific features
    this.addDomainFeatures = options.containsKey("domainFile");
//    if (addDomainFeatures) {
//      sourceIdInfoMap = SparseFeatureUtils.loadGenreFile(options.getProperty("domainFile"));
//    }

    mlrt = null;
    featureTags = null;
  }

  /**
   * Constructor for the generative model.
   * 
   * @param mlrt
   */
  public LexicalReorderingFeaturizer1(LexicalReorderingTable mlrt) {
    this.mlrt = mlrt;
    useAlignmentConstellations = false;
    featureTags = new String[mlrt.positionalMapping.length];
    for (int i = 0; i < mlrt.positionalMapping.length; i++) {
      featureTags[i] = String.format("%s:%s", FEATURE_PREFIX,
          mlrt.positionalMapping[i]);
    }
    discriminativeSet = null;
    addDomainFeatures = false;
    countFeatureIndex = -1;
  }
  
  @Override
  public void initialize(int sourceInputId,
      List<ConcreteRule<IString,String>> options, Sequence<IString> foreign) {
  }

  @Override
  public List<FeatureValue<String>> featurize(
      Featurizable<IString, String> f) {
    final List<FeatureValue<String>> features = Generics.newLinkedList();
    final boolean monotone = f.linearDistortion == 0;
    final boolean swap = (f.prior != null && f.sourcePosition
        + f.sourcePhrase.size() == f.prior.sourcePosition);

    // Discriminative model
    if (discriminativeSet != null && aboveThreshold(f.rule)) {
      for (LexicalReorderingTable.ReorderingTypes mrt : discriminativeSet) {
        if (!featureFunction(monotone, swap, mrt))
          continue;
     
        // Thang Jan14: reorganize to use multi class files
        if (DEBUG_OPT>0){
          System.err.println("# lexical reordering");
        }
        List<String> ruleReps = new ArrayList<String>();
        if (usePrior(mrt)) {

          if (useAlignmentConstellations) {
            IString priorAlignConst = (f.prior != null ? f.prior.rule.abstractRule.alignment
                .toIString() : INITIAL_PHRASE.get(0));
            ruleReps.add(priorAlignConst.toString());
          
          } else {
            ruleReps = getDiscriminativeRepresentation(f.prior);
          }
        } else {
          if(useAlignmentConstellations){
            ruleReps.add(f.rule.abstractRule.alignment.toString());
          } else {
            ruleReps = getDiscriminativeRepresentation(f);
          }

        }
        
        for (int i = 0; i < ruleReps.size(); i++) {
          String featureString = DISCRIMINATIVE_PREFIX + FEATURE_PREFIX + i + ":" + mrt + ":"
              + ruleReps.get(i);
          if (DEBUG_OPT>0){
            System.err.println("  " + featureString);
          }
          features.add(new FeatureValue<String>(featureString, 1.0));
          if (addDomainFeatures && sourceIdInfoMap.containsKey(f.sourceInputId)) {
            Pair<String,Integer> genreInfo = sourceIdInfoMap.get(f.sourceInputId);
            String genre = genreInfo.first();
            features.add(new FeatureValue<String>(featureString + "-" + genre, 1.0));
          }
        }
      }
    }

    // Generative model
    if (mlrt != null) {
      float[] scores = mlrt
          .getReorderingScores(f.derivation.rule.abstractRule);
      float[] priorScores = (f.prior == null ? null : mlrt
          .getReorderingScores(f.prior.derivation.rule.abstractRule));

      if (DETAILED_DEBUG) {
        System.err.printf("%s(%d) => %s(%d)\n", f.sourcePhrase,
            f.sourcePosition, f.targetPhrase, f.targetPosition);
        if (f.prior == null)
          System.err.printf("Prior <s> => <s>\n");
        else
          System.err.printf("Prior %s(%d) => %s(%d)\n", f.sourcePhrase,
              f.sourcePosition, f.targetPhrase, f.targetPosition);
        System.err.printf("Monotone: %s\nSwap: %s\n", monotone, swap);
        System.err.printf("PriorScores: %s\nScores: %s\n",
            (priorScores == null ? "null" : Arrays.toString(priorScores)),
            (scores == null ? "null" : Arrays.toString(scores)));
      }

      for (int i = 0; i < mlrt.positionalMapping.length; i++) {
        boolean ff = featureFunction(monotone, swap, mlrt.positionalMapping[i]);
        if (!usePrior(mlrt.positionalMapping[i])) {
          if (scores != null && ff)
            features.add(new FeatureValue<String>(featureTags[i], scores[i]));
        } else {
          if (priorScores != null && ff)
            features
                .add(new FeatureValue<String>(featureTags[i], priorScores[i]));
        }
      }
    }
    if (DETAILED_DEBUG) {
      System.err.printf("Feature values:\n");
      for (FeatureValue<String> value : features)
        System.err.printf("\t%s: %f\n", value.name, value.value);
    }
    
    // Create the state
    int rightEdge = lastOptionRightEdge(f.derivation);
    int leftEdge = lastOptionLeftEdge(f.derivation);
    f.setState(this, new MSDState(leftEdge, rightEdge, f.derivation.sourceCoverage));

    return features;
  }

  private boolean aboveThreshold(ConcreteRule<IString, String> rule) {
    if (countFeatureIndex < 0 || useClasses) return true;
    if (countFeatureIndex >= rule.abstractRule.scores.length) {
      // Generated by unknown word model...don't know count.
      return false;
    }
    double count = Math.exp(rule.abstractRule.scores[countFeatureIndex]);
    return count > LEXICAL_FEATURE_CUTOFF;
  }
  
  /**
   * Return a representation of the rule contained by the <code>Featurizable</code>.
   * 
   * @param f
   * @return
   */
  private List<String> getDiscriminativeRepresentation(Featurizable<IString, String> f) {
    List<String> reps = new ArrayList<String>();
    if (f == null) {
      reps.add(String.format("%s>%s", INITIAL_PHRASE, INITIAL_PHRASE));
    } else if (useClasses) {
      /* Thang Jan14: handle multiple class files */
      int numClasses = sourceMap.getNumMappings();
      List<StringBuilder> sbs = new ArrayList<StringBuilder>(); // build numClasses StringBuilder in parallel
      for (int i = 0; i < numClasses; i++) {
        sbs.add(new StringBuilder());
      }
      // src
      if (DEBUG_OPT>0){
        System.err.print("  src:");
      }
      for (IString token : f.rule.abstractRule.source) {
        if (DEBUG_OPT>0){
          System.err.print(" " + token.toString());
        }
        
        List<IString> tokens = sourceMap.getList(token);
        for (int i = 0; i < numClasses; i++) {
          StringBuilder sb = sbs.get(i);
          if (sb.length() > 0) sb.append("-");
          sb.append(tokens.get(i).toString());
        }
      }
      if (DEBUG_OPT>0){
        System.err.print("\n  tgt:");
      }
      
      // delimiter
      for (int i = 0; i < numClasses; i++) {
        sbs.get(i).append(">");
      }
      // tgt
      boolean seenFirst = false;
      for (IString token : f.rule.abstractRule.target) {
        if (DEBUG_OPT>0){
          System.err.print(" " + token.toString());
        }
        
        List<IString> tokens = targetMap.getList(token);
        for (int i = 0; i < numClasses; i++) {
          StringBuilder sb = sbs.get(i);
          
          if (seenFirst) sb.append("-");
          sb.append(tokens.get(i).toString());
        }
        
        seenFirst = true;
      }
      if (DEBUG_OPT>0){
        System.err.println();
      }
      
      // feature strings
      for (StringBuilder sb : sbs) {
        reps.add(sb.toString());
      }
    } else {
      // Lexicalized
      String sourcePhrase = f.sourcePhrase.toString("-");
      String targetPhrase = f.targetPhrase.toString("-");
      reps.add(String.format("%s>%s", sourcePhrase, targetPhrase));
    }
    
    return reps;
  }

  private boolean usePrior(LexicalReorderingTable.ReorderingTypes type) {
    switch (type) {
    case monotoneWithNext:
    case swapWithNext:
    case discontinuousWithNext:
    case nonMonotoneWithNext:
      return true;
    default:
      break;
    }
    return false;
  }

  private boolean featureFunction(boolean monotone, boolean swap,
      LexicalReorderingTable.ReorderingTypes type) {
    switch (type) {
    case monotoneWithPrevious:
    case monotoneWithNext:
      return monotone;
    case swapWithPrevious:
    case swapWithNext:
      return swap;
    case discontinuousWithPrevious:
    case discontinuousWithNext:
      return !(monotone || swap);
    case nonMonotoneWithPrevious:
    case nonMonotoneWithNext:
      return !monotone;
    }
    return false;
  }
  
  private static int lastOptionLeftEdge(Derivation<IString, String> hyp) {
    if (hyp.rule == null)
      return -1;
    return hyp.rule.sourcePosition - 1;
  }

  private static int lastOptionRightEdge(Derivation<IString, String> hyp) {
    if (hyp.rule == null)
      return 0;
    return hyp.rule.sourceCoverage.length();
  }
  
  private static class MSDState extends FeaturizerState {

    private final int leftEdge;
    private final int rightEdge;
    private final CoverageSet sourceCoverage;

    public MSDState(int leftEdge, int rightEdge, CoverageSet sourceCoverage) {
      this.leftEdge = leftEdge;
      this.rightEdge = rightEdge;
      this.sourceCoverage = sourceCoverage;
    }
    
    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      } else if ( ! (other instanceof MSDState)) {
        return false;
      } else {
        MSDState o = (MSDState) other;
        return equals(this, o);
      }
    }
    
    private boolean equals(MSDState stateA, MSDState stateB) {
      if (stateA.rightEdge != stateB.rightEdge)
        // same as LinearDistortionRecombinationFilter:
        return false;
      int leftA = stateA.leftEdge;
      int leftB = stateB.leftEdge;
      if (leftA == leftB)
        return true;

      // Now hypA and hypB may look like this:
      // hypA: y y . . . . x x . . . z z
      // hypB: y y y . . x x x . . . z z
      // where y,z stand for coverage set of previous options (the number of y and
      // z may be zero),
      // and x stands for coverage set of the current option.
      // If the next option (represented with n's) is generated to the right of
      // x's,
      // hypA and hypB will always receive the same orientation, i.e.:
      // (A) Both monotone:
      // hypA: y y . . . . x x n n . z z
      // hypB: y y y . . x x x n n . z z
      // (B) Both discontinuous:
      // hypA: y y . . . . x x . n n z z
      // hypB: y y y . . x x x . n n z z
      // If the next option is generated to the left, we have two cases:
      // (C) Both discontinuous:
      // hypA: y y . n . . x x . . . z z
      // hypB: y y y n . x x x . . . z z
      // (D) One discontinuous, one swap:
      // hypA: y y . . n . x x . . . z z
      // hypB: y y y . n x x x . . . z z
      // So we only need to worry about case (D). The function should return false
      // if case (D) is possible. The condition that makes (D) impossible is:
      // the number of words between the last y and the first x is zero for either
      // hypA or hypB. In this condition is true, (D) is impossible, thus
      // the next option will always receive the same orientation (no matter where
      // it appears), thus hypA and hypB are combinable, thus return true.

      if (leftA < 0 || leftB < 0)
        // Nothing to the left of either hypA or hypB, so (D) is impossible:
        return true;

      if (!stateA.sourceCoverage.get(leftA) && !stateB.sourceCoverage.get(leftA)) {
        // (D) is possible as shown here:
        // hypA: y y . . n x x x . . . z z
        // hypB: y y y . n . x x . . . z z
        return false;
      }

      if (!stateA.sourceCoverage.get(leftB) && !stateB.sourceCoverage.get(leftB)) {
        // (D) is possible as shown here:
        // hypA: y y . . n . x x . . . z z
        // hypB: y y y . n x x x . . . z z
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      return rightEdge;
    }
  }
}
