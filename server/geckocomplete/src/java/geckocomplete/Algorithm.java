package geckocomplete;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
import java.lang.StringBuilder;

final public class Algorithm {
   public static Boolean _is_initialized = false;
   public static Boolean[] IsUpperCase = new Boolean[0xFFFFF];
   public static Character[] ToLowerCase = new Character[0xFFFFF];

   public static void init() {
      if (Algorithm._is_initialized) return;
      // System.out.println("computing lookup tables...");

      for (int i = 0; i < 0xFFFFF; ++i) {
         IsUpperCase[i] = Character.isUpperCase((char)i);
         ToLowerCase[i] = Character.toLowerCase((char)i);
      }

      Algorithm._is_initialized = true;
   }

   // longest common substring algorith derived from
   // https://stackoverflow.com/questions/34805488/finding-all-the-common-substrings-of-given-two-strings
   public static Set<String> longestCommonSubstring(String s, String t) {
      int[][] table = new int[s.length()][t.length()];

      int longest = 0;
      Set<String> result = new HashSet<>(); 

      for (int i = 0; i < s.length(); ++i) {
         for (int j = 0; j < t.length(); ++j) {
            if (s.charAt(i) != t.charAt(j)) {
               continue;
            }

            table[i][j] = (i == 0 || j == 0) ? 1 : 1 + table[i-1][j-1];

            if (table[i][j] > longest) {
               longest = table[i][j];
               result.clear();
            }
            if (table[i][j] == longest) {
               result.add(s.substring(i - longest + 1, i + 1));
            }
         }
      }
      return result;
   }

   public static int allCommonSubstringSimilarityScore(String s, String t) {
      int sn = s.length();
      int tn = t.length();

      // prevent degenerate cases from causing slow downs.
      if (sn > 100 | tn > 100) {
         return 0;
      }

      int[][] table = new int[sn][tn];

      // build dynamic programming table
      for (int i = 0; i < sn; ++i) {
         for (int j = 0; j < tn; ++j) {
            if (s.charAt(i) != t.charAt(j)) {
               continue;
            }

            table[i][j] = (i == 0 || j == 0) ? 1 : 1 + table[i-1][j-1];
         }
      }

      int score = 0;
      for (int i = sn - 1; i >= 0; --i) {
         for (int j = tn - 1; j >= 0; --j) {
            int x = table[i][j];
            if (x >= 2) {
               if (i == sn - 1 || j == tn - 1) {
                  score += x;
               } else if (table[i + 1][j + 1] == 0) {
                  score += x;
               }
            }
         }
      }

      return score;
   }

   public static String wordBoundaries(String word) {
      if (word.length() < 3) {
         return null;
      }
      StringBuilder boundaries = new StringBuilder();
      char ch = word.charAt(0);
      boundaries.append(ToLowerCase[(int)ch]);
      int n = word.length() - 1;
      for (int i = 1; i < n; ++i) {
         char c = word.charAt(i);
         char c2 = word.charAt(i + 1);
         
         if (c == '_' || c == '-') {
            boundaries.append(ToLowerCase[(int)c2]);
         } else if (!IsUpperCase[(int)c] && IsUpperCase[(int)c2]) {
            boundaries.append(ToLowerCase[(int)c2]);
            ++i;
         }
      }
      if (boundaries.length() == 1) {
         return null;
      } else {
         return boundaries.toString();
      }
   }

   public static Boolean isSubSequence(String s, String t) {
      int n_s = s.length();
      int n_t = t.length();
      if (n_t > n_s) return false;

      int j = 0;
      for (int i = 0; i < n_s; ++i) {
         char ch = s.charAt(i);
         if (j >= n_t) break;
         char ch2 = t.charAt(j);
         if (ToLowerCase[(int)ch] == ToLowerCase[(int)ch2]) {
            ++j;
         }
      }

      return j == n_t;
   }
}
