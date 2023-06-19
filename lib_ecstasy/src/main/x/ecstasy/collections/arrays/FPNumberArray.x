/**
 * Functionality specific to arrays of floating point numbers.
 */
mixin FPNumberArray<Element extends FPNumber>
        into Array<Element>
        extends NumberArray<Element> {

    typedef Number.Rounding as Rounding;

    // ----- vector operations ---------------------------------------------------------------------

    /**
     * For each value in this array, round the value to an integer amount, using the specified
     * rounding direction.
     *
     * @param direction  the optional rounding direction specifier
     * @param inPlace    (optional) pass True to specify that the operation should occur using
     *                   `this` to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray round(Rounding direction = TiesToAway, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].round(direction);
            }
            return this;
        }

        return new Element[size](i -> this[i].round(direction)).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the greatest integer value less than or equal to
     * the floating point value.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray floor(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].floor();
            }
            return this;
        }

        return new Element[size](i -> this[i].floor()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the least integer value greater than or equal to the
     * floating point value.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray ceil(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].ceil();
            }
            return this;
        }

        return new Element[size](i -> this[i].ceil()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate Euler's number raised to the power of the floating
     * point value.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray exp(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].exp();
            }
            return this;
        }

        return new Element[size](i -> this[i].exp()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, scale the value by an power of its radix; this is analogous to
     * "shifting the decimal point" in a number, and may be more efficient than using multiplication
     * or division.
     *
     * @param power   the number of places to "shift the point"
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results, each one formed by the original
     *         value scaled by `radix` raised to the specified `power`
     */
    FPNumberArray scaleByPow(Int power, Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].scaleByPow(power);
            }
            return this;
        }

        return new Element[size](i -> this[i].scaleByPow(power)).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, scale the value by an power of its radix; this is analogous to
     * "shifting the decimal point" in a number, and may be more efficient than using multiplication
     * or division.
     *
     * @param powers  the corresponding number of places to "shift the point"
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results, each one formed by the original
     *         value scaled by `radix` raised to the power of `n`
     */
    FPNumberArray scaleByPow(Int[] powers, Boolean inPlace = False) {
        assert:bounds this.size == powers.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].scaleByPow(powers[i]);
            }
            return this;
        }

        return new Element[size](i -> this[i].scaleByPow(powers[i])).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the natural logarithm (base _e_).
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray log(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].log();
            }
            return this;
        }

        return new Element[size](i -> this[i].log()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the base 2 logarithm.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray log2(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].log2();
            }
            return this;
        }

        return new Element[size](i -> this[i].log2()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the base 10 logarithm.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray log10(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].log10();
            }
            return this;
        }

        return new Element[size](i -> this[i].log10()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the square root.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray sqrt(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].sqrt();
            }
            return this;
        }

        return new Element[size](i -> this[i].sqrt()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the cubic root.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray cbrt(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].cbrt();
            }
            return this;
        }

        return new Element[size](i -> this[i].cbrt()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the sine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray sin(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].sin();
            }
            return this;
        }

        return new Element[size](i -> this[i].sin()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the cosine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray cos(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].cos();
            }
            return this;
        }

        return new Element[size](i -> this[i].cos()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the tangent.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray tan(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].tan();
            }
            return this;
        }

        return new Element[size](i -> this[i].tan()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the arc sine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray asin(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].asin();
            }
            return this;
        }

        return new Element[size](i -> this[i].asin()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the arc cosine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray acos(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].acos();
            }
            return this;
        }

        return new Element[size](i -> this[i].acos()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the arc tangent.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray atan(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].atan();
            }
            return this;
        }

        return new Element[size](i -> this[i].atan()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assumed to be an x value, and each value in the passed array,
     * assumed to be a y value, calculate the arc tangent of y/x, expressed in radians.
     *
     * @param y       an array of y coordinates
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray atan2(Element[] y, Boolean inPlace = False) {
        assert:bounds this.size == y.size;

        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].atan2(y[i]);
            }
            return this;
        }

        return new Element[size](i -> this[i].atan2(y[i])).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assuming that the value represents a hyperbolic angle,
     * calculate the hyperbolic sine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray sinh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].sinh();
            }
            return this;
        }

        return new Element[size](i -> this[i].sinh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assuming that the value represents a hyperbolic angle,
     * calculate the hyperbolic cosine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray cosh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].cosh();
            }
            return this;
        }

        return new Element[size](i -> this[i].cosh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assumed to represent a hyperbolic angle, calculate the
     * hyperbolic tangent.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray tanh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].tanh();
            }
            return this;
        }

        return new Element[size](i -> this[i].tanh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the area hyperbolic sine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray asinh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].asinh();
            }
            return this;
        }

        return new Element[size](i -> this[i].asinh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the area hyperbolic cosine.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the floating point results
     */
    FPNumberArray acosh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].acosh();
            }
            return this;
        }

        return new Element[size](i -> this[i].acosh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, calculate the area hyperbolic tangent of the value.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the area hyperbolic tangents
     */
    FPNumberArray atanh(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].atanh();
            }
            return this;
        }

        return new Element[size](i -> this[i].atanh()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assume the value is a number of degrees, and calculate the
     * number of radians corresponding to the degrees.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the radian values
     */
    FPNumberArray deg2rad(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].deg2rad();
            }
            return this;
        }

        return new Element[size](i -> this[i].deg2rad()).toArray(mutability, inPlace=True);
    }

    /**
     * For each value in this array, assume the value is a number of radians, and calculate the
     * number of degrees corresponding to the radians.
     *
     * @param inPlace (optional) pass True to specify that the operation should occur using `this`
     *                to hold the result, if possible
     *
     * @return the array containing the degree values
     */
    FPNumberArray rad2deg(Boolean inPlace = False) {
        if (inPlace && this.inPlace) {
            for (Int i : 0 ..< size) {
                this[i] = this[i].rad2deg();
            }
            return this;
        }

        return new Element[size](i -> this[i].rad2deg()).toArray(mutability, inPlace=True);
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Obtain an array of integer values corresponding to the values in this array, using an
     * optional rounding approach and an optional coercion function.
     *
     * @param rounding     (optional) specifies the rounding mechanism to use to obtain integer
     *                     values from the floating point values in this array
     * @param outOfBounds  (optional) the value to use as a substitute for values out-of-bounds on
     *                     the positive side of the range (and for the negative side of the range if
     *                     the optional `negBounds` value is not provided)
     * @param NaN          (optional) the value to use as a substitute for all NaN values
     * @param infinite     (optional) the value to use as a substitute for positive infinity (and
     *                     for negative infinity if the optional `negInfinity` value is not
     *                     provided)
     * @param negBounds    (optional) the value to use as a substitute for values out-of-bounds on
     *                     the negative side of the range; defaults to the value specified for
     *                     `outOfBounds`
     * @param negInfinity  (optional) the value to use as a substitute for negative infinite;
     *                     defaults to the value specified for `infinite`
     *
     * @return an array of Int8 values
     *
     * @throws OutOfBounds  if the floating point values cannot be mapped to an integer equivalent,
     *                      either by rounding as specified, or coercing as specified
     */
    <ResultType extends IntNumber> ResultType[] toIntNumberArray(
            Type<ResultType> resultType,
            Mutability       mutability      = Constant,
            Rounding         rounding        = TiesToAway,
            ResultType?      outOfBounds     = Null,
            ResultType?      NaN             = Null,
            ResultType?      infinite        = Null,
            ResultType?      negBounds       = Null,
            ResultType?      negInfinity     = Null) {
        assert Range<Number> range := ResultType.range()
                as $"{ResultType} a is not fixed length integer type";
        assert range.is(Range<ResultType>);

        function Element(ResultType) elementOf = ResultType.converterTo(Element);
        Element min = elementOf(range.effectiveLowerBound);
        Element max = elementOf(range.effectiveUpperBound);

        function ResultType(Element) convert = Element.converterTo(ResultType);

        function ResultType(Element) coerce = e -> {
            if (e.finite) {
                Element v = e.round(rounding);
                return v < min
                        ? (negBounds ?: outOfBounds ?: throw new OutOfBounds("val={v}, min={min}"))
                        : v > max
                                ? (outOfBounds ?: throw new OutOfBounds("val={v}, max={max}"))
                                : convert(v);
            }

            if (e.NaN) {
                return NaN ?: throw new OutOfBounds("value is NaN");
            }

            if (e.negative) {
                return negInfinity?;
            }

            return infinite ?: throw new OutOfBounds("value is infinite");
        };

        return new ResultType[size](i -> coerce(this[i])).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Dec values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Dec values drawn from this array
     */
    Dec[] toDecArray(Mutability mutability = Constant) {
        return Element == Dec
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Dec[size](i -> this[i].toDec()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Dec32 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Dec32 values drawn from this array
     */
    Dec32[] toDec32Array(Mutability mutability = Constant) {
        return Element == Dec32
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Dec32[size](i -> this[i].toDec32()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Dec64 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Dec64 values drawn from this array
     */
    Dec64[] toDec64Array(Mutability mutability = Constant) {
        return Element == Dec64
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Dec64[size](i -> this[i].toDec64()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Dec128 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Dec128 values drawn from this array
     */
    Dec128[] toDec128Array(Mutability mutability = Constant) {
        return Element == Dec128
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Dec128[size](i -> this[i].toDec128()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float8e4 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float8e4 values drawn from this array
     */
    Float8e4[] toFloat8e4Array(Mutability mutability = Constant) {
        return Element == Float8e4
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float8e4[size](i -> this[i].toFloat8e4()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float8e5 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float8e5 values drawn from this array
     */
    Float8e5[] toFloat8e5Array(Mutability mutability = Constant) {
        return Element == Float8e5
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float8e5[size](i -> this[i].toFloat8e5()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to BFloat16 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing BFloat16 values drawn from this array
     */
    BFloat16[] toBFloat16Array(Mutability mutability = Constant) {
        return Element == BFloat16
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new BFloat16[size](i -> this[i].toBFloat16()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float16 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float16 values drawn from this array
     */
    Float16[] toFloat16Array(Mutability mutability = Constant) {
        return Element == Float16
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float16[size](i -> this[i].toFloat16()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float32 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float32 values drawn from this array
     */
    Float32[] toFloat32Array(Mutability mutability = Constant) {
        return Element == Float32
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float32[size](i -> this[i].toFloat32()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float64 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float64 values drawn from this array
     */
    Float64[] toFloat64Array(Mutability mutability = Constant) {
        return Element == Float64
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float64[size](i -> this[i].toFloat64()).toArray(mutability, True);
    }

    /**
     * Obtain copy of this array's floating point values, converted to Float128 values.
     *
     * @param mutability  the mutability of the resulting array
     *
     * @return an array of the specified mutability containing Float128 values drawn from this array
     */
    Float128[] toFloat128Array(Mutability mutability = Constant) {
        return Element == Float128
                ? (this.mutability <= Persistent
                        ? this.toArray(mutability)
                        : new Array(mutability, this))
                : new Float128[size](i -> this[i].toFloat128()).toArray(mutability, True);
    }


    // ----- aggregations --------------------------------------------------------------------------

    /**
     * Compute variance of the array values.
     *
     * @return True iff the array is not empty
     * @return (optional) the variance
     */
    conditional Element variance() {
        Int size = this.size;
        if (size == 0) {
            return False;
        }
        assert Element sum := sum();
        Element        cnt  = Int.converterTo(Element)(size);
        Element mean = sum/cnt;

        sum = Element.zero();
        for (Element value : this) {
            Element diff = value - mean;
            sum += diff*diff;
        }
        return True, sum/cnt;
    }
}