/*
// Licensed to Julian Hyde under one or more contributor license
// agreements. See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.
//
// Julian Hyde licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except in
// compliance with the License. You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
package org.eigenbase.sql.type;

import java.util.*;

import org.eigenbase.reltype.*;
import org.eigenbase.resource.*;
import org.eigenbase.sql.*;


/**
 * Parameter type-checking strategy where all operand types must be the same.
 *
 * @author Wael Chatila
 * @version $Id$
 */
public class SameOperandTypeChecker
    implements SqlOperandTypeChecker
{
    //~ Instance fields --------------------------------------------------------

    protected final int nOperands;

    //~ Constructors -----------------------------------------------------------

    public SameOperandTypeChecker(
        int nOperands)
    {
        this.nOperands = nOperands;
    }

    //~ Methods ----------------------------------------------------------------

    // implement SqlOperandTypeChecker
    public boolean checkOperandTypes(
        SqlCallBinding callBinding,
        boolean throwOnFailure)
    {
        return checkOperandTypesImpl(
            callBinding,
            throwOnFailure,
            callBinding);
    }

    private boolean checkOperandTypesImpl(
        SqlOperatorBinding operatorBinding,
        boolean throwOnFailure,
        SqlCallBinding callBinding)
    {
        int nOperandsActual = nOperands;
        if (nOperandsActual == -1) {
            nOperandsActual = operatorBinding.getOperandCount();
        }
        assert !(throwOnFailure && (callBinding == null));
        RelDataType [] types = new RelDataType[nOperandsActual];
        for (int i = 0; i < nOperandsActual; ++i) {
            if (operatorBinding.isOperandNull(i, false)) {
                if (throwOnFailure) {
                    throw callBinding.getValidator().newValidationError(
                        callBinding.getCall().operands[i],
                        EigenbaseResource.instance().NullIllegal.ex());
                } else {
                    return false;
                }
            }
            types[i] = operatorBinding.getOperandType(i);
        }
        for (int i = 1; i < nOperandsActual; ++i) {
            if (!SqlTypeUtil.isComparable(types[i], types[i - 1])) {
                if (!throwOnFailure) {
                    return false;
                }

                // REVIEW jvs 5-June-2005: Why don't we use
                // newValidationSignatureError() here?  It gives more
                // specific diagnostics.
                throw callBinding.newValidationError(
                    EigenbaseResource.instance().NeedSameTypeParameter.ex());
            }
        }
        return true;
    }

    /**
     * Similar functionality to {@link #checkOperandTypes(SqlCallBinding,
     * boolean)}, but not part of the interface, and cannot throw an error.
     */
    public boolean checkOperandTypes(
        SqlOperatorBinding operatorBinding)
    {
        return checkOperandTypesImpl(operatorBinding, false, null);
    }

    // implement SqlOperandTypeChecker
    public SqlOperandCountRange getOperandCountRange()
    {
        if (nOperands == -1) {
            return SqlOperandCountRange.Variadic;
        } else {
            return new SqlOperandCountRange(nOperands);
        }
    }

    // implement SqlOperandTypeChecker
    public String getAllowedSignatures(SqlOperator op, String opName)
    {
        int nOperandsActual = nOperands;
        if (nOperandsActual == -1) {
            nOperandsActual = 3;
        }
        String [] array = new String[nOperandsActual];
        Arrays.fill(array, "EQUIVALENT_TYPE");
        if (nOperands == -1) {
            array[2] = "...";
        }
        return SqlUtil.getAliasedSignature(
            op,
            opName,
            Arrays.asList(array));
    }
}

// End SameOperandTypeChecker.java
