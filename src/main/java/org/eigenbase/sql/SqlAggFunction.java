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
package org.eigenbase.sql;

import org.eigenbase.rel.*;
import org.eigenbase.sql.type.*;
import org.eigenbase.sql.validate.*;


/**
 * Abstract base class for the definition of an aggregate function: an operator
 * which aggregates sets of values into a result.
 *
 * @author jhyde
 * @version $Id$
 */
public abstract class SqlAggFunction
    extends SqlFunction
    implements Aggregation
{
    //~ Constructors -----------------------------------------------------------

    public SqlAggFunction(
        String name,
        SqlKind kind,
        SqlReturnTypeInference returnTypeInference,
        SqlOperandTypeInference operandTypeInference,
        SqlOperandTypeChecker operandTypeChecker,
        SqlFunctionCategory funcType)
    {
        super(
            name,
            kind,
            returnTypeInference,
            operandTypeInference,
            operandTypeChecker,
            funcType);
    }

    //~ Methods ----------------------------------------------------------------

    public boolean isQuantifierAllowed()
    {
        return true;
    }

    // override SqlFunction
    public void validateCall(
        SqlCall call,
        SqlValidator validator,
        SqlValidatorScope scope,
        SqlValidatorScope operandScope)
    {
        super.validateCall(call, validator, scope, operandScope);
        validator.validateAggregateParams(call, scope);
    }
}

// End SqlAggFunction.java
