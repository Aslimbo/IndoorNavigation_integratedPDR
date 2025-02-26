package com.example.locate;

import java.util.ArrayList;
import java.util.List;

public class custommath {
    public static float getAverage(List<Float> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("List must not be null or empty");
        }

        float sum = 0;
        for (Float num : list) {
            sum += num;
        }
        return sum / list.size();
    }

    public static float calculateDistance(float x1, float y1, float x2, float y2) {
        float xDiff = x2 - x1;
        float yDiff = y2 - y1;
        return (float) Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }
    public static void addEntry_limitsize(ArrayList<Float> list, int size,  Float x) {
        if (list.size() < size) {
            list.add(x);  // Add the entry if size is less than 10
        } else {
            list.remove(0);  // Remove the first entry if size is 10
            list.add(x);  // Add the new entry at the end
        }
    }
}
