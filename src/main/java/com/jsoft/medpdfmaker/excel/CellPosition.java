package com.jsoft.medpdfmaker.excel;

import java.util.Objects;

/**
 * User: jin
 * Date: 6/23/13 11:22 PM
 * Version: 1.0
 */
public class CellPosition {

    private final int x;

    private final int y;

    public CellPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CellPosition that = (CellPosition) o;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "CellPosition{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
