/*
 * This file is part of ReplayStudio, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 johni0702 <https://github.com/johni0702>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.replaymod.replaystudio.pathing.interpolation;

import com.replaymod.replaystudio.pathing.interpolation.PolynomialSplineInterpolator.Polynomial;
import com.replaymod.replaystudio.pathing.path.Keyframe;
import com.replaymod.replaystudio.pathing.path.PathSegment;
import com.replaymod.replaystudio.pathing.property.Property;
import com.replaymod.replaystudio.pathing.property.PropertyPart;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class CatmullRomSplineInterpolator extends AbstractInterpolator {

    @Getter
    private final double alpha;

    private Map<PropertyPart<?>, Polynomial[]> cubicPolynomials = new HashMap<>();
    private Map<Property<?>, Set<Keyframe>> framesToProperty = new HashMap<>();

    private void addToMap(Property property, Keyframe keyframe) {
        Set<Keyframe> set = framesToProperty.get(property);
        if (set == null) {
            framesToProperty.put(property, set = new LinkedHashSet<>());
        }
        set.add(keyframe);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<PropertyPart, InterpolationParameters> bakeInterpolation(Map<PropertyPart, InterpolationParameters> parameters) {
        framesToProperty.clear();
        for (PathSegment segment : getSegments()) {
            for (Property property : getKeyframeProperties()) {
                if (segment.getStartKeyframe().getValue(property).isPresent()) {
                    addToMap(property, segment.getStartKeyframe());
                }
                if (segment.getEndKeyframe().getValue(property).isPresent()) {
                    addToMap(property, segment.getEndKeyframe());
                }
            }
        }

        calcPolynomials();

        Map<PropertyPart, InterpolationParameters> lastParameters = new HashMap<>();

        for (Property<?> property : getKeyframeProperties()) {
            for (PropertyPart<?> part : property.getParts()) {
                Polynomial[] polynomials = cubicPolynomials.get(part);
                Polynomial last = polynomials[polynomials.length - 1];

                double value = last.eval(1);
                double velocity = last.derivative().eval(1);
                double acceleration = last.derivative().derivative().eval(1);

                lastParameters.put(part, new InterpolationParameters(value, velocity, acceleration));
            }
        }

        return lastParameters;
    }

    // http://steve.hollasch.net/cgindex/curves/catmull-rom.html
    protected void calcPolynomials() {
        for (Map.Entry<Property<?>, Set<Keyframe>> e : framesToProperty.entrySet()) {
            Property<?> property = e.getKey();
            Set<Keyframe> keyframes = e.getValue();

            for (PropertyPart<?> part : property.getParts()) {
                if (!part.isInterpolatable()) continue;

                List<Double> values = new ArrayList<>();

                if (Double.isNaN(part.getUpperBound())) {
                    for (Keyframe k : keyframes) {
                        values.add(getValueAsDouble(k, part));
                    }
                } else {
                    double bound = part.getUpperBound();
                    double halfBound = bound / 2;

                    Iterator<Keyframe> it = keyframes.iterator();

                    Double lastValue = null;
                    Integer offset = null;

                    while (it.hasNext()) {
                        Keyframe keyframe = it.next();
                        double value = mod(getValueAsDouble(keyframe, part), bound);

                        if (lastValue == null) {
                            lastValue = value;
                            offset = (int) Math.floor(value / bound);
                        }

                        if (Math.abs(value - lastValue) > halfBound) {
                            // We can wrap around to get to the new value quicker
                            if (lastValue < halfBound) {
                                offset--; // Wrap around the bottom
                            } else {
                                offset++; // Wrap around the top
                            }
                        }

                        values.add(value + offset * bound);
                        lastValue = value;
                    }
                }

                Polynomial[] polynomials = new Polynomial[values.size()-1];

                for (int i=0; i<values.size()-1; i++) {
                    double p0, p1, p2, p3;

                    p1 = values.get(i);
                    p2 = values.get(i+1);

                    if (i > 0) {
                        p0 = values.get(i-1);
                    } else {
                        p0 = p1;
                    }

                    if (i < keyframes.size() - 2) {
                        p3 = values.get(i+2);
                    } else {
                        p3 = p2;
                    }

                    double t0 = alpha * (p2 - p0);
                    double t1 = alpha * (p3 - p1);

                    double[] c = new double[] {
                            2* p1 - 2* p2 + t0 + t1,
                            -3* p1 + 3* p2 - 2*t0 - t1,
                            t0,
                            p1
                    };

                    polynomials[i] = new Polynomial(c);
                }

                cubicPolynomials.put(part, polynomials);
            }
        }
    }

    // copied from PolynomialSplineInterpolator - move this in a utils class?
    private double mod(double val, double m) {
        double off = Math.floor(val / m);
        return val - off * m;
    }

    // Helper method because generics cannot be defined on blocks
    private <T> double getValueAsDouble(Keyframe keyframe, PropertyPart<T> part) {
        return part.toDouble(keyframe.getValue(part.getProperty()).get());
    }

    @Override
    public <T> Optional<T> getValue(Property<T> property, long time) {
        Set<Keyframe> kfSet = framesToProperty.get(property);
        if (kfSet == null) {
            return Optional.empty();
        }

        T valueBefore = null;
        long timeBefore = -1, timeAfter = -1;
        int index = 0;
        int i = 0;
        for (Keyframe keyframe : kfSet) {
            if (keyframe.getTime() == time) {
                return keyframe.getValue(property);
            } else if (keyframe.getTime() < time) {
                index = i;
                timeBefore = keyframe.getTime();
                valueBefore = keyframe.getValue(property).get();
            } else if (keyframe.getTime() > time) {
                timeAfter = keyframe.getTime();
                break;
            }
            i++;
        }

        if (timeBefore == -1 || timeAfter == -1) {
            return Optional.empty();
        }

        double fraction = (time - timeBefore) / (double) (timeAfter - timeBefore);

        T interpolated = valueBefore;

        for (PropertyPart<T> part : property.getParts()) {
            if (!part.isInterpolatable()) continue;

            Polynomial[] polynomials = cubicPolynomials.get(part);
            interpolated = part.fromDouble(interpolated, polynomials[index].eval(fraction));
        }
        return Optional.of(interpolated);
    }

}
