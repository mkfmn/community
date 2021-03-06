/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.index.impl.lucene;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;
import org.neo4j.index.lucene.QueryContext;

abstract class IndexType
{
    private static final IndexType EXACT = new IndexType( LuceneDataSource.KEYWORD_ANALYZER, false )
    {
        @Override
        public Query deletionQuery( long entityId, String key, Object value )
        {
            BooleanQuery q = new BooleanQuery();
            q.add( idTermQuery( entityId ), Occur.MUST );
            q.add( new TermQuery( new Term( key, value.toString() ) ), Occur.MUST );
            return q;
        }

        @Override
        public Query get( String key, Object value )
        {
            return new TermQuery( new Term( key, value.toString() ) );
        }

        @Override
        public void addToDocument( Document document, String key, Object value )
        {
            document.add( instantiateField( key, value, Index.NOT_ANALYZED ) );
        }

        void removeFieldsFromDocument( Document document, String key, Object value )
        {
            Set<String> values = null;
            if ( value != null )
            {
                String stringValue = value.toString();
                values = new HashSet<String>( Arrays.asList(
                        document.getValues( key ) ) );
                if ( !values.remove( stringValue ) )
                {
                    return;
                }
            }
            document.removeFields( key );
            if ( value != null )
            {
                for ( String existingValue : values )
                {
                    addToDocument( document, key, existingValue );
                }
            }
        }

        @Override
        public String toString()
        {
            return "EXACT";
        }
    };
    
    private static class CustomType extends IndexType
    {
        private final Similarity similarity;
        
        CustomType( Analyzer analyzer, boolean toLowerCase, Similarity similarity )
        {
            super( analyzer, toLowerCase );
            this.similarity = similarity;
        }
        
        @Override
        Similarity getSimilarity()
        {
            return this.similarity;
        }
        
        @Override
        public Query deletionQuery( long entityId, String key, Object value )
        {
            BooleanQuery q = new BooleanQuery();
            q.add( idTermQuery( entityId ), Occur.MUST );
            q.add( new TermQuery( new Term( exactKey( key ), value.toString() ) ), Occur.MUST );
            return q;
        }

        @Override
        public Query get( String key, Object value )
        {
            return new TermQuery( new Term( exactKey( key ), value.toString() ) );
        }

        private String exactKey( String key )
        {
            return key + "_e";
        }

        @Override
        public void addToDocument( Document document, String key, Object value )
        {
            document.add( new Field( exactKey( key ), value.toString(), Store.YES, Index.NOT_ANALYZED ) );
            document.add( instantiateField( key, value, Index.ANALYZED ) );
        }
        
        @Override
        void removeFieldsFromDocument( Document document, String key, Object value )
        {
            String exactKey = exactKey( key );
            Set<String> values = null;
            if ( value != null )
            {
                String stringValue = value.toString();
                values = new HashSet<String>( Arrays.asList( document.getValues( exactKey ) ) );
                if ( !values.remove( stringValue ) )
                {
                    return;
                }
            }
            document.removeFields( exactKey );
            document.removeFields( key );
            if ( value != null )
            {
                for ( String existingValue : values )
                {
                    addToDocument( document, key, existingValue );
                }
            }
        }

        @Override
        public String toString()
        {
            return "FULLTEXT";
        }
    };
    
    final Analyzer analyzer;
    private final boolean toLowerCase;
    
    private IndexType( Analyzer analyzer, boolean toLowerCase )
    {
        this.analyzer = analyzer;
        this.toLowerCase = toLowerCase;
    }
    
    static IndexType getIndexType( IndexIdentifier identifier, Map<String, String> config )
    {
        String type = config.get( LuceneIndexImplementation.KEY_TYPE );
        IndexType result = null;
        Similarity similarity = getCustomSimilarity( config );
        boolean toLowerCase = parseBoolean( config.get( LuceneIndexImplementation.KEY_TO_LOWER_CASE ), true );
        Analyzer customAnalyzer = getCustomAnalyzer( config );
        if ( type != null )
        {
            // Use the built in alternatives... "exact" or "fulltext"
            if ( type.equals( "exact" ) )
            {
                result = EXACT;
            }
            else if ( type.equals( "fulltext" ) )
            {
                Analyzer analyzer = customAnalyzer;
                if ( analyzer == null )
                {
                    analyzer = toLowerCase ? LuceneDataSource.LOWER_CASE_WHITESPACE_ANALYZER :
                            LuceneDataSource.WHITESPACE_ANALYZER;
                }
                result = new CustomType( analyzer, toLowerCase, similarity );
            }
        }
        else
        {
            // Use custom analyzer
            if ( customAnalyzer == null )
            {
                throw new IllegalArgumentException( "No 'type' was given (which can point out " +
                		"built-in analyzers, such as 'exact' and 'fulltext')" +
                		" and no 'analyzer' was given either (which can point out a custom " +
                		Analyzer.class.getName() + " to use)" );
            }
            result = new CustomType( customAnalyzer, toLowerCase, similarity );
        }
        return result;
    }

    private static boolean parseBoolean( String string, boolean valueIfNull )
    {
        return string == null ? valueIfNull : Boolean.parseBoolean( string );
    }
    
    private static Similarity getCustomSimilarity( Map<String, String> config )
    {
        return getByClassName( config, LuceneIndexImplementation.KEY_SIMILARITY, Similarity.class );
    }
    
    private static Analyzer getCustomAnalyzer( Map<String, String> config )
    {
        return getByClassName( config, LuceneIndexImplementation.KEY_ANALYZER, Analyzer.class );
    }

    private static <T> T getByClassName( Map<String, String> config, String configKey, Class<T> cls )
    {
        String className = config.get( configKey );
        if ( className != null )
        {
            try
            {
                return Class.forName( className ).asSubclass( cls ).newInstance();
            }
            catch ( Exception e )
            {
                throw new RuntimeException( e );
            }
        }
        return null;
    }

    abstract Query deletionQuery( long entityId, String key, Object value );
    
    abstract Query get( String key, Object value );
    
    TxData newTxData( LuceneIndex index )
    {
        return new ExactTxData( index );
    }
    
    Query query( String keyOrNull, Object value, QueryContext contextOrNull )
    {
        if ( value instanceof Query )
        {
            return (Query) value;
        }
        
        QueryParser parser = new QueryParser( Version.LUCENE_30, keyOrNull, analyzer );
        parser.setAllowLeadingWildcard( true );
        parser.setLowercaseExpandedTerms( toLowerCase );
        if ( contextOrNull != null && contextOrNull.getDefaultOperator() != null )
        {
            parser.setDefaultOperator( contextOrNull.getDefaultOperator() );
        }
        try
        {
            return parser.parse( value.toString() );
        }
        catch ( ParseException e )
        {
            throw new RuntimeException( e );
        }
    }
    
    abstract void addToDocument( Document document, String key, Object value );
    
    Fieldable instantiateField( String key, Object value, Index analyzed )
    {
        Fieldable field = null;
        if ( value instanceof Number )
        {
            Number number = (Number) value;
            NumericField numberField = new NumericField( key, Store.YES, true );
            if ( value instanceof Long )
            {
                numberField.setLongValue( number.longValue() );
            }
            else if ( value instanceof Float )
            {
                numberField.setFloatValue( number.floatValue() );
            }
            else if ( value instanceof Double )
            {
                numberField.setDoubleValue( number.doubleValue() );
            }
            else
            {
                numberField.setIntValue( number.intValue() );
            }
            field = numberField;
        }
        else
        {
            field = new Field( key, value.toString(), Store.YES, analyzed );
        }
        return field;
    }
    
    final void removeFromDocument( Document document, String key, Object value )
    {
        if ( key == null && value == null )
        {
            clearDocument( document );
        }
        else
        {
            removeFieldsFromDocument( document, key, value );
        }
    }
    
    abstract void removeFieldsFromDocument( Document document, String key, Object value );

    private void clearDocument( Document document )
    {
        Set<String> names = new HashSet<String>();
        for ( Fieldable field : document.getFields() )
        {
            names.add( field.name() );
        }
        names.remove( LuceneIndex.KEY_DOC_ID );
        for ( String name : names )
        {
            document.removeFields( name );
        }
    }
    
    static Document newBaseDocument( long entityId )
    {
        Document doc = new Document();
        doc.add( new Field( LuceneIndex.KEY_DOC_ID, "" + entityId, Store.YES,
                Index.NOT_ANALYZED ) );
        return doc;
    }

    Term idTerm( long entityId )
    {
        return new Term( LuceneIndex.KEY_DOC_ID, "" + entityId );
    }
    
    Query idTermQuery( long entityId )
    {
        return new TermQuery( idTerm( entityId ) );
    }
    
    Similarity getSimilarity()
    {
        return null;
    }
}
