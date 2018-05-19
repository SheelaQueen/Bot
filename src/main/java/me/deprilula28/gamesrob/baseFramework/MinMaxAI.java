package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MinMaxAI {
    @FunctionalInterface
    public static interface BranchProcessor {
        void walk(Branch branch);
    }

    @Data
    public static class Branch {
        private List<Double> elements = new ArrayList<>();

        public double getAvarage() {
            return elements.stream().mapToDouble(it -> it).sum() / (double) elements.size();
        }

        public void walk(BranchProcessor processor) {
            Branch itemBranch = new Branch();
            processor.walk(itemBranch);
            elements.add(itemBranch.getAvarage());
        }

        public void node(double number) {
            elements.add(number);
        }
    }

    public static int use(BranchProcessor processor) {
        Branch master = new Branch();
        processor.walk(master);

        return master.getElements().indexOf(master.getElements().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingDouble(it -> it))).findFirst().orElse(1.0));
    }
}
