package com.example.locate.data;
import java.util.ArrayList;
import java.util.List;

public class obstacleRegion {
    private List<Position> positions;

    public obstacleRegion() {
        this.positions = new ArrayList<>();
    }

    public void addPosition(Position position) {
        positions.add(position);
    }

    public List<Position> getPositions() {
        return positions;
    }

    @Override
    public String toString() {
        return "obstacleRegion{points =" + positions + "}";
    }
}
