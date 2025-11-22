package org.civworld.brainrots.repo;

import lombok.Getter;
import org.civworld.brainrots.model.BrainrotModel;

import java.util.HashSet;

public class BrainrotRepo {
    @Getter private final HashSet<BrainrotModel> brainrots = new HashSet<>();

    public void addBrainrot(BrainrotModel brainrot){
        brainrots.add(brainrot);
    }
}
