package org.civworld.brainrots.repo;

import lombok.Getter;
import org.civworld.brainrots.model.BrainrotModel;

import java.util.HashSet;

public class BrainrotRepo {
    @Getter private final HashSet<BrainrotModel> brainrots = new HashSet<>();

    public void addBrainrot(BrainrotModel brainrot){
        brainrots.add(brainrot);
    }

    public void removeBrainrot(String id){
        brainrots.removeIf(brainrot -> brainrot.getId().equals(id));
    }

    public BrainrotModel getById(String id){
        if (id == null) return null;
        String needle = id.trim();
        for(BrainrotModel brainrotModel : brainrots){
            if(brainrotModel.getId().equalsIgnoreCase(needle)) return brainrotModel;
        }
        return null;
    }

    public boolean hasBrainrotById(String id){
        if (id == null) return false;
        String needle = id.trim();
        for(BrainrotModel brainrot : brainrots){
            if(brainrot.getId().equalsIgnoreCase(needle)) return true;
        }

        return false;
    }
}
