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
        for(BrainrotModel brainrotModel : brainrots){
            if(brainrotModel.getId().equals(id)) return brainrotModel;
        }
        return null;
    }

    public boolean hasBrainrotById(String id){
        for(BrainrotModel brainrot : brainrots){
            if(brainrot.getId().equals(id)) return true;
        }

        return false;
    }
}
