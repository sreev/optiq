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
package net.hydromatic.optiq.jdbc;

import net.hydromatic.linq4j.*;
import net.hydromatic.linq4j.expressions.Expression;
import net.hydromatic.linq4j.expressions.Expressions;
import net.hydromatic.linq4j.function.Function1;
import net.hydromatic.linq4j.function.Predicate1;

import net.hydromatic.optiq.DataContext;
import net.hydromatic.optiq.Schema;
import net.hydromatic.optiq.Table;
import net.hydromatic.optiq.impl.java.MapSchema;

import org.eigenbase.reltype.*;
import org.eigenbase.runtime.*;
import org.eigenbase.util.Util;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * Helper for implementing the {@code getXxx} methods such as
 * {@link OptiqDatabaseMetaData#getTables}.
 */
public class Meta {
    final OptiqConnectionImpl connection;

    public Meta(OptiqConnectionImpl connection) {
        this.connection = connection;
    }

    static <T extends Named> Predicate1<T> matcher(
        final String pattern)
    {
        return new Predicate1<T>() {
            public boolean apply(T v1) {
                return matches(v1, pattern);
            }
        };
    }

    /** Creates the data dictionary, also called the information schema. It is a
     * schema called "metadata" that contains tables "TABLES", "COLUMNS" etc. */
    MapSchema createInformationSchema() {
        final MapSchema mapSchema =
            MapSchema.create(
                connection, connection.getRootSchema(), "metadata");
        mapSchema.addTable(
            "TABLES",
            new MetadataTable<MetaTable>(
                connection,
                mapSchema,
                "TABLES",
                MetaTable.class)
            {
                public Enumerator<MetaTable> enumerator() {
                    return schemas(connection.getCatalog())
                        .selectMany(
                            new Function1<MetaSchema, Enumerable<MetaTable>>() {
                                public Enumerable<MetaTable> apply(
                                    MetaSchema a0)
                                {
                                    return tables(a0);
                                }
                            })
                        .enumerator();
                }
            });
        mapSchema.addTable(
            "COLUMNS",
            new MetadataTable<MetaColumn>(
                connection,
                mapSchema,
                "COLUMNS",
                MetaColumn.class)
            {
                public Enumerator<MetaColumn> enumerator() {
                    return schemas(connection.getCatalog())
                        .selectMany(
                            new Function1<MetaSchema, Enumerable<MetaTable>>() {
                                public Enumerable<MetaTable> apply(
                                    MetaSchema a0)
                                {
                                    return tables(a0);
                                }
                            })
                        .selectMany(
                            new Function1<MetaTable, Enumerable<MetaColumn>>() {
                                public Enumerable<MetaColumn> apply(
                                    MetaTable a0)
                                {
                                    return columns(a0);
                                }
                            })
                        .enumerator();
                }
            });
        return mapSchema;
    }

    ResultSet getTables(
        String catalog,
        final String schemaPattern,
        String tableNamePattern,
        String[] types) throws SQLException
    {
        return IteratorResultSet.create(
            schemas(catalog)
                .where(
                    Meta.<MetaSchema>matcher(schemaPattern))
                .selectMany(
                    new Function1<MetaSchema, Enumerable<MetaTable>>() {
                        public Enumerable<MetaTable> apply(MetaSchema a0) {
                            return tables(a0);
                        }
                    })
                .where(
                    Meta.<MetaTable>matcher(tableNamePattern))
                .iterator(),
            new NamedFieldGetter(
                MetaTable.class,
                "TABLE_CAT",
                "TABLE_SCHEM",
                "TABLE_NAME",
                "TABLE_TYPE",
                "REMARKS",
                "TYPE_CAT",
                "TYPE_SCHEM",
                "TYPE_NAME",
                "SELF_REFERENCING_COL_NAME",
                "REF_GENERATION"));
    }

    ResultSet getColumns(
        String catalog,
        String schemaPattern,
        String tableNamePattern,
        String columnNamePattern)
    {
        return IteratorResultSet.create(
            schemas(catalog)
                .where(
                    Meta.<MetaSchema>matcher(schemaPattern))
                .selectMany(
                    new Function1<MetaSchema, Enumerable<MetaTable>>() {
                        public Enumerable<MetaTable> apply(MetaSchema a0) {
                            return tables(a0);
                        }
                    })
                .where(
                    Meta.<MetaTable>matcher(tableNamePattern))
                .selectMany(
                    new Function1<MetaTable, Enumerable<MetaColumn>>() {
                        public Enumerable<MetaColumn> apply(MetaTable a0) {
                            return columns(a0);
                        }
                    })
                .where(
                    Meta.<MetaColumn>matcher(columnNamePattern))
                .iterator(),
            new NamedFieldGetter(
                MetaColumn.class,
                "TABLE_CAT",
                "TABLE_SCHEM",
                "TABLE_NAME",
                "COLUMN_NAME",
                "DATA_TYPE",
                "TYPE_NAME",
                "COLUMN_SIZE",
                "BUFFER_LENGTH",
                "DECIMAL_DIGITS",
                "NUM_PREC_RADIX",
                "NULLABLE",
                "REMARKS",
                "COLUMN_DEF",
                "SQL_DATA_TYPE",
                "SQL_DATETIME_SUB",
                "CHAR_OCTET_LENGTH",
                "ORDINAL_POSITION",
                "IS_NULLABLE",
                "SCOPE_CATALOG",
                "SCOPE_TABLE",
                "SOURCE_DATA_TYPE",
                "IS_AUTOINCREMENT",
                "IS_GENERATEDCOLUMN"));
    }

    Enumerable<MetaSchema> schemas(String catalog) {
        Collection<String> schemaNames =
            connection.rootSchema.getSubSchemaNames();
        return Linq4j.asEnumerable(schemaNames)
            .select(
                new Function1<String, MetaSchema>() {
                    public MetaSchema apply(String name) {
                        return new MetaSchema(
                            connection.rootSchema.getSubSchema(name),
                            connection.getCatalog(),
                            name);
                    }
                });
    }

    Enumerable<MetaTable> tables(final MetaSchema schema) {
        Collection<String> tableNames = schema.optiqSchema.getTableNames();
        return Linq4j.asEnumerable(tableNames)
            .select(
                new Function1<String, MetaTable>() {
                    public MetaTable apply(String name) {
                        return new MetaTable(
                            schema.optiqSchema.getTable(name, Object.class),
                            schema.catalogName, schema.schemaName, name);
                    }
                });
    }

    public static boolean matches(Named element, String pattern) {
        return pattern == null
               || pattern.equals("%")
               || element.getName().equals(pattern); // TODO: better wildcard
    }

    public Enumerable<MetaColumn> columns(final MetaTable table) {
        return Linq4j.asEnumerable(table.optiqTable.getRowType().getFieldList())
            .select(
                new Function1<RelDataTypeField, MetaColumn>() {
                    public MetaColumn apply(RelDataTypeField a0) {
                        final int precision =
                            a0.getType().getSqlTypeName().allowsPrec()
                            && !(a0.getType()
                                 instanceof RelDataTypeFactoryImpl.JavaType)
                                ? a0.getType().getPrecision()
                                : -1;
                        return new MetaColumn(
                            table.tableCat,
                            table.tableSchem,
                            table.tableName,
                            a0.getName(),
                            a0.getType().getSqlTypeName().getJdbcOrdinal(),
                            a0.getType().getFullTypeString(),
                            precision,
                            a0.getType().getSqlTypeName().allowsScale()
                                ? a0.getType().getScale()
                                : null,
                            10,
                            a0.getType().isNullable()
                                ? DatabaseMetaData.columnNullable
                                : DatabaseMetaData.columnNoNulls,
                            precision,
                            a0.getIndex() + 1,
                            a0.getType().isNullable() ? "YES" : "NO");
                    }
                }
            );
    }

    interface Named {
        String getName();
    }

    public static class MetaColumn implements Named {
        public final String tableCat;
        public final String tableSchem;
        public final String tableName;
        public final String columnName;
        public final int dataType;
        public final String typeName;
        public final int columnSize;
        public final String bufferLength = null;
        public final Integer decimalDigits;
        public final int numPrecRadix;
        public final int nullable;
        public final String remarks = null;
        public final String columnDef = null;
        public final String sqlDataType = null;
        public final String sqlDatetimeSub = null;
        public final int charOctetLength;
        public final int ordinalPosition;
        public final String isNullable;
        public final String scopeCatalog = null;
        public final String scopeTable = null;
        public final String sourceDataType = null;
        public final String isAutoincrement = null;
        public final String isGeneratedcolumn = null;

        MetaColumn(
            String tableCat,
            String tableSchem,
            String tableName,
            String columnName,
            int dataType,
            String typeName,
            int columnSize,
            Integer decimalDigits,
            int numPrecRadix,
            int nullable,
            int charOctetLength,
            int ordinalPosition,
            String isNullable)
        {
            this.tableCat = tableCat;
            this.tableSchem = tableSchem;
            this.tableName = tableName;
            this.columnName = columnName;
            this.dataType = dataType;
            this.typeName = typeName;
            this.columnSize = columnSize;
            this.decimalDigits = decimalDigits;
            this.numPrecRadix = numPrecRadix;
            this.nullable = nullable;
            this.charOctetLength = charOctetLength;
            this.ordinalPosition = ordinalPosition;
            this.isNullable = isNullable;
        }

        public String getName() {
            return columnName;
        }
    }

    public static class MetaTable implements Named {
        private final Table optiqTable;
        public final String tableCat;
        public final String tableSchem;
        public final String tableName;
        public final String tableType = null;
        public final String remarks = null;
        public final String typeCat = null;
        public final String typeSchem = null;
        public final String typeName = null;
        public final String selfReferencingColName = null;
        public final String refGeneration = null;

        public MetaTable(
            Table optiqTable,
            String tableCat,
            String tableSchem,
            String tableName)
        {
            this.optiqTable = optiqTable;
            this.tableCat = tableCat;
            this.tableSchem = tableSchem;
            this.tableName = tableName;
        }

        public String getName() {
            return tableName;
        }
    }

    static class MetaSchema implements Named {
        private final Schema optiqSchema;
        public final String catalogName;
        public final String schemaName;

        public MetaSchema(
            Schema optiqSchema,
            String catalogName,
            String schemaName)
        {
            this.optiqSchema = optiqSchema;
            this.catalogName = catalogName;
            this.schemaName = schemaName;
        }

        public String getName() {
            return schemaName;
        }
    }

    private static class NamedFieldGetter
        implements AbstractIterResultSet.ColumnGetter
    {
        private final Field[] fields;
        private final String[] columnNames;

        public NamedFieldGetter(Class clazz, String... names) {
            final List<String> columnNameList = new ArrayList<String>();
            final List<Field> fieldList = new ArrayList<Field>();
            StringBuilder buf = new StringBuilder();
            for (String name : names) {
                columnNameList.add(name);
                buf.setLength(0);
                int nextUpper = -1;
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == '_') {
                        nextUpper = i + 1;
                        continue;
                    }
                    if (nextUpper == i) {
                        c = Character.toUpperCase(c);
                    } else {
                        c = Character.toLowerCase(c);
                    }
                    buf.append(c);
                }
                String fieldName = buf.toString();
                try {
                    fieldList.add(clazz.getField(fieldName));
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
            this.fields = fieldList.toArray(new Field[fieldList.size()]);
            this.columnNames =
                columnNameList.toArray(new String[columnNameList.size()]);
        }

        public String [] getColumnNames()
        {
            return columnNames;
        }

        public Object get(
            Object o,
            int columnIndex)
        {
            try {
                return fields[columnIndex - 1].get(o);
            } catch (IllegalArgumentException e) {
                throw Util.newInternal(
                    e,
                    "Error while retrieving field " + fields[columnIndex - 1]);
            } catch (IllegalAccessException e) {
                throw Util.newInternal(
                    e,
                    "Error while retrieving field " + fields[columnIndex - 1]);
            }
        }
    }

    private static abstract class MetadataTable<E>
        extends AbstractQueryable<E>
        implements Table<E>
    {
        private final MapSchema schema;
        private final String tableName;
        private final Class<E> clazz;
        private final OptiqConnectionImpl connection;

        public MetadataTable(
            OptiqConnectionImpl connection, MapSchema schema, String tableName,
            Class<E> clazz)
        {
            super();
            this.schema = schema;
            this.tableName = tableName;
            this.clazz = clazz;
            this.connection = connection;
        }

        public DataContext getDataContext() {
            return schema;
        }

        public RelDataType getRowType() {
            return connection.typeFactory.createType(getElementType());
        }

        public Class<E> getElementType() {
            return clazz;
        }

        public Expression getExpression() {
            return Expressions.call(
                schema.getExpression(),
                "getTable",
                Expressions.<Expression>list()
                    .append(Expressions.constant(tableName))
                    .append(Expressions.constant(getElementType())));
        }

        public QueryProvider getProvider() {
            return connection;
        }

        public Iterator<E> iterator() {
            return Linq4j.enumeratorIterator(enumerator());
        }
    }
}

// End Meta.java
