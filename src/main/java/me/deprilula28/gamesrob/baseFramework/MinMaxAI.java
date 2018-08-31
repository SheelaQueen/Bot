package me.deprilula28.gamesrob.baseFramework;

import lombok.Data;
import me.deprilula28.gamesrob.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MinMaxAI {
    public static final ThreadPoolExecutor MULTI_THREADED_AI_EXEC = new ThreadPoolExecutor(40, 40,
            Integer.MAX_VALUE, TimeUnit.HOURS, new LinkedBlockingQueue<>());

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

        public double walk(BranchProcessor processor) {
            Branch itemBranch = new Branch();
            processor.walk(itemBranch);

            double avg = itemBranch.getAvarage();
            elements.add(avg);
            return avg;
        }

        public void node(double number) {
            elements.add(number);
        }
    }

    public static void queue(BranchProcessor processor) {

    }

    public static int use(BranchProcessor processor) {
        Branch master = new Branch();

        processor.walk(master);
        return master.getElements().indexOf(master.getElements().stream()
                .min(Collections.reverseOrder(Comparator.comparingDouble(it -> it))).orElse(1.0));
    }
}
