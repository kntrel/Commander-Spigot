package com.kntrel.mc.commander.command;

import java.util.NoSuchElementException;

public class Argument {

    private final String value_;
    private final Integer intValue_;
    private final Double doubleValue_;
    private final Boolean boolValue_;
    private final boolean isRelative_;

    Argument(String value) {
        this.value_ = value;

        boolean isRelative = value.startsWith("~");

        String numString = isRelative ? value.substring(1) : value;
        if (isRelative && numString.length() < 1){
            this.intValue_ = 0;
            this.doubleValue_ = 0.0;
            this.boolValue_ = null;
            this.isRelative_ = true;
            return;
        }

        Integer i;
        try {
            i = Integer.parseInt(numString);
        } catch (NumberFormatException e) {
            i = null;
        }
        this.intValue_ = i;

        Double d;
        try {
            d = Double.parseDouble(numString);
        } catch (NumberFormatException e) {
            d = null;
        }
        this.doubleValue_ = d;

        this.isRelative_ = (this.intValue_ != null || this.doubleValue_ != null) && isRelative;

        Boolean b;
        if (value.equalsIgnoreCase("true")) {
            b = true;
        } else if (value.equalsIgnoreCase("false")) {
            b = false;
        } else if (this.isInt()) {
            b = this.getInt() != 0;
        } else {
            b = null;
        }
        this.boolValue_ = b;

    }

    public String getString() {
        return value_;
    }
    public boolean isInt() {
        return this.intValue_ != null;
    }
    public boolean isDouble() {
        return this.doubleValue_ != null;
    }
    public boolean isBool() {
        return this.boolValue_ != null;
    }
    public boolean isRelative() {
        return isRelative_;
    }

    public int getInt() {
        try {
            return this.intValue_.intValue();
        } catch (NullPointerException e) {
            throw new NoSuchElementException("This argument is not an integer");
        }
    }
    public double getDouble() {
        try {
            return this.doubleValue_.doubleValue();
        } catch (NullPointerException e) {
            throw new NoSuchElementException("This argument is not a boolean");
        }
    }
    public boolean getBool() {
        try {
            return this.boolValue_.booleanValue();
        } catch (NullPointerException e) {
            throw new NoSuchElementException("This argument is not a boolean");
        }
    }

    @Override
    public String toString() {
        return this.value_;
    }


}
