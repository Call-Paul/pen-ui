package org.six11.skrui.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.six11.skrui.shape.Primitive;
import org.six11.util.Debug;

/**
 * 
 * 
 * @author Gabe Johnson <johnsogg@cmu.edu>
 */
public class VoteTable {
  private Map<String, Map<Primitive, Integer>> votes;
  private Map<String, Map<Primitive, Map<String, List<Primitive>>>> cells;

  public VoteTable() {
    votes = new HashMap<String, Map<Primitive, Integer>>();
    cells = new HashMap<String, Map<Primitive, Map<String, List<Primitive>>>>(); // yes I did that
  }

  public void addVote(String slot, Primitive prim) {
    if (isValid(slot, prim)) {
      votes.get(slot).put(prim, votes.get(slot).get(prim) + 1);
      bug(slot + "/" + prim.getShortStr() + ": " + votes.get(slot).get(prim) + " votes...");
    }
  }

  public boolean isValid(String slot, Primitive prim) {
    return getValue(slot, prim) >= 0;
  }

  public int getValue(String slot, Primitive prim) {
    maybeInit(slot, prim);
    return votes.get(slot).get(prim);
  }

  private void maybeInit(String slot, Primitive prim) {
    if (votes.get(slot) == null) {
      votes.put(slot, new HashMap<Primitive, Integer>());
    }
    if (votes.get(slot).get(prim) == null) {
      votes.get(slot).put(prim, 0);
    }
  }

  public void invalidate(String slot, Primitive prim) {
    bug("Invalidating slot/prim: " + slot + "/" + prim.getShortStr());
    maybeInit(slot, prim);
    votes.get(slot).put(prim, -1);
  }

  private static void bug(String what) {
    Debug.out("VoteTable", what);
  }

  public void setVotes(String slot, Primitive p, Map<String, List<Primitive>> consolidated) {
    if (!cells.containsKey(slot)) {
      cells.put(slot, new HashMap<Primitive, Map<String, List<Primitive>>>());
    }
    cells.get(slot).put(p, consolidated);
  }

  public void activateVotes() {
    for (String slotName : cells.keySet()) {
      for (Primitive prim : cells.get(slotName).keySet()) {
        // only cast votes from valid cells.
        if (isValid(slotName, prim)) {
          Map<String, List<Primitive>> cellVotes = cells.get(slotName).get(prim);
          for (String targetName : cellVotes.keySet()) {
            for (Primitive targetPrim : cellVotes.get(targetName)) {
              // this will only cast votes for valid cells.
              addVote(targetName, targetPrim);
            }
          }
        }
      }
    }
    bug("Done voting. Results:");
    for (String slotName : votes.keySet()) {
      bug(slotName + ":");
      int best = 0;
      for (Primitive prim : votes.get(slotName).keySet()) {
        best = Math.max(votes.get(slotName).get(prim), best);
      }
      for (Primitive prim : votes.get(slotName).keySet()) {
        int count = votes.get(slotName).get(prim);
        bug("  " + prim.getShortStr() + ": " + votes.get(slotName).get(prim) + " votes " + count +
            (count == best ? " * winner" : ""));
      }
    }
  }
}
