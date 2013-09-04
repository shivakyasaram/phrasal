package edu.stanford.nlp.mt.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.mt.train.AlignmentTemplate;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 * A standard lexicalized reordering table.
 *
 * @author danielcer
 *
 */
public class LexicalReorderingTable {

  /**
   * Reordering types
   *
   * <pre>
   * Monotone with Previous      Monotone with Next
   *
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:
   *  F_0  PPP                   F_0
   *  F_1     PPP                F_1      PPP
   *  F_2                        F_2         PPP
   *
   *
   * Swap with Previous      Swap with Next
   *
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:
   *  F_0      PPP               F_0
   *  F_1  PPP                   F_1         PPP
   *  F_2                        F_2      PPP
   *
   * Discontinuous with Prev  Discontinuous with Next
   *
   *   e:  E_0 E_1 E_2            e:  E_0 E_1 E_2
   * f:                         f:
   *  F_0      PPP               F_0          PPP
   *  F_1                        F_1
   *  F_2  PPP                   F_2      PPP
   *
   * </pre>
   *
   * NonMonotone: Swap <em>or</em> Discontinuous.
   *
   * @author danielcer
   *
   */

  public enum ReorderingTypes {
    monotoneWithPrevious, swapWithPrevious, discontinuousWithPrevious, nonMonotoneWithPrevious, monotoneWithNext, swapWithNext, discontinuousWithNext, nonMonotoneWithNext
  }

  enum ConditionTypes {
    f, e, fe
  }

  static final ReorderingTypes[] msdPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious };

  static final ReorderingTypes[] msdBidirectionalPositionMapping = {
      ReorderingTypes.monotoneWithPrevious, ReorderingTypes.swapWithPrevious,
      ReorderingTypes.discontinuousWithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.swapWithNext,
      ReorderingTypes.discontinuousWithNext };

  static final ReorderingTypes[] monotonicityPositionalMapping = {
      ReorderingTypes.monotoneWithPrevious,
      ReorderingTypes.nonMonotoneWithPrevious };

  static final ReorderingTypes[] monotonicityBidirectionalMapping = {
      ReorderingTypes.monotoneWithPrevious,
      ReorderingTypes.nonMonotoneWithPrevious,
      ReorderingTypes.monotoneWithNext, ReorderingTypes.nonMonotoneWithNext };

  static final Map<String, Object> fileTypeToReorderingType = Generics.newHashMap();

  static {
    fileTypeToReorderingType.put("msd-fe", msdPositionMapping);
    fileTypeToReorderingType.put("msd-bidirectional-fe",
        msdBidirectionalPositionMapping);
    fileTypeToReorderingType.put("monotonicity-fe",
        monotonicityPositionalMapping);
    fileTypeToReorderingType.put("monotonicity-bidirectional-fe",
        monotonicityBidirectionalMapping);
    fileTypeToReorderingType.put("msd-f", msdPositionMapping);
    fileTypeToReorderingType.put("msd-bidirectional-f",
        msdBidirectionalPositionMapping);
    fileTypeToReorderingType.put("monotonicity-f",
        monotonicityPositionalMapping);
    fileTypeToReorderingType.put("monotonicity-bidirectional-f",
        monotonicityBidirectionalMapping);
  }

  static final Map<String, ConditionTypes> fileTypeToConditionType = Generics.newHashMap();
  
  static {
    fileTypeToConditionType.put("msd-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("msd-bidirectional-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-fe", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-bidirectional-fe",
        ConditionTypes.fe);
    fileTypeToConditionType.put("msd-f", ConditionTypes.fe);
    fileTypeToConditionType.put("msd-bidirectional-f", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-f", ConditionTypes.fe);
    fileTypeToConditionType.put("monotonicity-bidirectional-f",
        ConditionTypes.fe);
  }

  final String filetype;
  private List<float[]> reorderingScores;

  public final ReorderingTypes[] positionalMapping;
  public final ConditionTypes conditionType;

  private static int[] mergeInts(int[] array1, int[] array2) {
    return new int[] { FlatPhraseTable.foreignIndex.indexOf(array1),
        FlatPhraseTable.translationIndex.indexOf(array2) };
  }

  public float[] getReorderingScores(int phraseId) {
    int reorderingId = -1;
    if (conditionType == ConditionTypes.f) {
      reorderingId = FlatPhraseTable.translationIndex.get(phraseId)[0];
    } else if (conditionType == ConditionTypes.e) {
      reorderingId = FlatPhraseTable.translationIndex.get(phraseId)[1];
    } else if (conditionType == ConditionTypes.fe) {
      reorderingId = phraseId;
    }
    return reorderingId < 0 || reorderingId >= reorderingScores.size() ? 
        null : reorderingScores.get(reorderingId);
  }

  /**
   *
   * @throws IOException
   */
  public LexicalReorderingTable(String filename) throws IOException {
    int phraseTableSize = FlatPhraseTable.translationIndex.size();
    this.reorderingScores = new ArrayList<float[]>(phraseTableSize);
    for (int i = 0; i < phraseTableSize; ++i) reorderingScores.add(null);
    
    String filetype = init(filename, null);
    this.filetype = filetype;
    this.positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
        .get(filetype);
    this.conditionType = fileTypeToConditionType.get(filetype);

  }

  public LexicalReorderingTable(String filename, String desiredFileType)
      throws IOException {
    int phraseTableSize = FlatPhraseTable.translationIndex.size();
    this.reorderingScores = new ArrayList<float[]>(phraseTableSize);
    for (int i = 0; i < phraseTableSize; ++i) reorderingScores.add(null);
    
    String filetype = init(filename, desiredFileType);
    if (!desiredFileType.equals(filetype)) {
      throw new RuntimeException(String.format(
          "Reordering file '%s' of type %s not %s\n", filename, filetype,
          desiredFileType));
    }
    this.filetype = filetype;
    this.positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
        .get(filetype);
    this.conditionType = fileTypeToConditionType.get(filetype);

  }

  private String init(String filename, String type) throws IOException {
    Runtime rt = Runtime.getRuntime();
    long preTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    final long startTime = System.nanoTime();
    System.err.printf("Loading Lexical Reordering Table: %s%n", filename);
    ReorderingTypes[] positionalMapping = null;
    ConditionTypes conditionType = null;
    String selectedFiletype = null;
    if (type == null) {
      for (String filetype : fileTypeToReorderingType.keySet()) {
        if (filename.contains(filetype)) {
          positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
              .get(filetype);
          conditionType = fileTypeToConditionType.get(filetype);
          selectedFiletype = filetype;
          break;
        }
      }
    } else {
      positionalMapping = (ReorderingTypes[]) fileTypeToReorderingType
          .get(type);
      conditionType = fileTypeToConditionType.get(type);
      selectedFiletype = type;
    }

    if (positionalMapping == null) {
      throw new RuntimeException(String.format(
          "Unable to determine lexical re-ordering file type for: %s\n",
          filename));
    }

    LineNumberReader reader = IOTools.getReaderFromFile(filename);
    for (String line; (line = reader.readLine()) != null; ) {
      final List<List<String>> fields = StringUtils.splitFieldsFast(line, AlignmentTemplate.DELIM);
      
      List<String> srcTokens;
      List<String> tgtTokens = null;
      List<String> scoreList;
      if (fields.size() == 2) {
        // TODO(spenceg): This format is not used anymore. Deprecate this condition.
        srcTokens = fields.get(0);
        scoreList = fields.get(1);
        
      } else if (fields.size() == 3) {
        // Standard phrase table format without alignments
        srcTokens = fields.get(0);
        tgtTokens = fields.get(1);
        scoreList = fields.get(2);
        
      } else if (fields.size() == 5) {
        // Standard phrase table format with alignments
        srcTokens = fields.get(0);
        tgtTokens = fields.get(1);
        scoreList = fields.get(4);
        
      } else {
        throw new RuntimeException("Invalid re-ordering table line: " + String.valueOf(reader.getLineNumber()));
      }
      
      if (scoreList.size() != positionalMapping.length) {
        throw new RuntimeException(
            String
                .format(
                    "File type '%s' requires that %d scores be provided for each entry, however only %d were found (line %d)",
                    filetype, positionalMapping.length, scoreList.size(),
                    reader.getLineNumber()));
      }
      
      int[] indexInts; // = null;
      if (conditionType == ConditionTypes.e
          || conditionType == ConditionTypes.f) {
        IString[] tokens = IStrings.toIStringArray(srcTokens);
        indexInts = IStrings.toIntArray(tokens);
      
      } else {
        IString[] fTokens = IStrings.toIStringArray(srcTokens);
        int[] fIndexInts = IStrings.toIntArray(fTokens);
        IString[] eTokens = IStrings.toIStringArray(tgtTokens);
        int[] eIndexInts = IStrings.toIntArray(eTokens);
        indexInts = mergeInts(fIndexInts, eIndexInts);
      }

      float[] scores = new float[scoreList.size()];
      int scoreId = 0;
      for (String score : scoreList) {
        try {
          float featureScore = (float) Double.parseDouble(score);
          assert featureScore <= 0 : "Feature scores are not in log format";
          scores[scoreId++] = featureScore;

        } catch (NumberFormatException e) {
          throw new RuntimeException(String.format(
              "Can't parse %s as a number (line %d)", score,
              reader.getLineNumber()));
        }
      }

      // Lookup this rule in the phrase table
      int idx = FlatPhraseTable.translationIndex.indexOf(indexInts);
      if (idx < 0) {
        throw new RuntimeException(String.format("Phrase %d not in phrase table", reader.getLineNumber()));
      }
      if (reorderingScores.get(idx) != null) {
        throw new RuntimeException(String.format("Duplicate phrase %d in phrase table", reader.getLineNumber()));
      }
      reorderingScores.set(idx, scores);
    }
    reader.close();
    
    long postTableLoadMemUsed = rt.totalMemory() - rt.freeMemory();
    double elapsedTime = ((double) System.nanoTime() - startTime) / 1e9;
    System.err.printf(
        "Done loading reordering table: %s (mem used: %d MiB time: %.3fs)%n",
        filename, (postTableLoadMemUsed - preTableLoadMemUsed) / (1024 * 1024),
        elapsedTime);
    System.err.printf("Done loading %s%n", filename);

    return selectedFiletype;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.err
          .printf("Usage:\n\tjava LexicalReorderingTable (lexical reordering filename)\n");
      System.exit(-1);
    }

    LexicalReorderingTable mlrt = new LexicalReorderingTable(args[0]);

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("\n>");
    for (String query; (query = reader.readLine()) != null; ) {
      String[] fields = query.split("\\s*\\|\\|\\|\\s*");
      int[] foreign = IStrings.toIntArray(IStrings.toIStringArray(fields[0]
          .split("\\s+")));
      int[] translation = IStrings.toIntArray(IStrings.toIStringArray(fields[1]
          .split("\\s+")));
      int[] merged = mergeInts(foreign, translation);
      int id = FlatPhraseTable.translationIndex.indexOf(merged);
      float[] scores = mlrt.getReorderingScores(id);
      for (int i = 0; i < scores.length; i++) {
        System.out.printf("%s: %e\n", mlrt.positionalMapping[i], scores[i]);
      }
      System.out.print("\n>");
    }
    reader.close();
  }

}
