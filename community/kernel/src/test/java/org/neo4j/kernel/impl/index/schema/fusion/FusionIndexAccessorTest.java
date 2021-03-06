/*
 * Copyright (c) 2002-2018 "Neo Technology,"
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
package org.neo4j.kernel.impl.index.schema.fusion;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import org.neo4j.helpers.collection.BoundedIterable;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.schema.index.IndexDescriptor;
import org.neo4j.kernel.impl.index.schema.NativeSelector;
import org.neo4j.kernel.impl.index.schema.fusion.FusionSchemaIndexProvider.DropAction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexTestHelp.verifyFusionCloseThrowIfAllThrow;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexTestHelp.verifyFusionCloseThrowOnSingleCloseThrow;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexTestHelp.verifyOtherIsClosedOnSingleThrow;

public class FusionIndexAccessorTest
{
    private IndexAccessor nativeAccessor;
    private IndexAccessor spatialAccessor;
    private IndexAccessor luceneAccessor;
    private FusionIndexAccessor fusionIndexAccessor;
    private final long indexId = 10;
    private final DropAction dropAction = mock( DropAction.class );

    @Before
    public void setup()
    {
        nativeAccessor = mock( IndexAccessor.class );
        spatialAccessor = mock( IndexAccessor.class );
        luceneAccessor = mock( IndexAccessor.class );
        fusionIndexAccessor = new FusionIndexAccessor( nativeAccessor, spatialAccessor, luceneAccessor,
                new NativeSelector(), indexId, mock( IndexDescriptor.class ), dropAction );
    }

    /* drop */

    @Test
    public void dropMustDropNativeAndLucene() throws Exception
    {
        // when
        // ... both drop successful
        fusionIndexAccessor.drop();
        // then
        verify( nativeAccessor, times( 1 ) ).drop();
        verify( spatialAccessor, times( 1 ) ).drop();
        verify( luceneAccessor, times( 1 ) ).drop();
        verify( dropAction ).drop( indexId );
    }

    @Test
    public void dropMustThrowIfDropNativeFail() throws Exception
    {
        // when
        verifyFailOnSingleDropFailure( nativeAccessor, fusionIndexAccessor );
    }

    @Test
    public void dropMustThrowIfDropSpatialFail() throws Exception
    {
        // when
        verifyFailOnSingleDropFailure( spatialAccessor, fusionIndexAccessor );
    }

    @Test
    public void dropMustThrowIfDropLuceneFail() throws Exception
    {
        // when
        verifyFailOnSingleDropFailure( luceneAccessor, fusionIndexAccessor );
    }

    private void verifyFailOnSingleDropFailure( IndexAccessor failingAccessor, FusionIndexAccessor fusionIndexAccessor )
            throws IOException
    {
        IOException expectedFailure = new IOException( "fail" );
        doThrow( expectedFailure ).when( failingAccessor ).drop();
        try
        {
            fusionIndexAccessor.drop();
            fail( "Should have failed" );
        }
        catch ( IOException e )
        {
            assertSame( expectedFailure, e );
        }
    }

    @Test
    public void dropMustThrowIfBothFail() throws Exception
    {
        // given
        IOException nativeFailure = new IOException( "native" );
        IOException spatialFailure = new IOException( "spatial" );
        IOException luceneFailure = new IOException( "lucene" );
        doThrow( nativeFailure ).when( nativeAccessor ).drop();
        doThrow( spatialFailure ).when( spatialAccessor ).drop();
        doThrow( luceneFailure ).when( luceneAccessor ).drop();

        try
        {
            // when
            fusionIndexAccessor.drop();
            fail( "Should have failed" );
        }
        catch ( IOException e )
        {
            // then
            assertThat( e, anyOf( sameInstance( nativeFailure ), sameInstance( spatialFailure ), sameInstance( luceneFailure ) ) );
        }
    }

    /* close */

    @Test
    public void closeMustCloseNativeAndLucene() throws Exception
    {
        // when
        // ... both drop successful
        fusionIndexAccessor.close();

        // then
        verify( nativeAccessor, times( 1 ) ).close();
        verify( spatialAccessor, times( 1 ) ).close();
        verify( luceneAccessor, times( 1 ) ).close();
    }

    @Test
    public void closeMustThrowIfLuceneThrow() throws Exception
    {
        verifyFusionCloseThrowOnSingleCloseThrow( luceneAccessor, fusionIndexAccessor );
    }

    @Test
    public void closeMustThrowIfSpatialThrow() throws Exception
    {
        verifyFusionCloseThrowOnSingleCloseThrow( spatialAccessor, fusionIndexAccessor );
    }

    @Test
    public void closeMustThrowIfNativeThrow() throws Exception
    {
        verifyFusionCloseThrowOnSingleCloseThrow( nativeAccessor, fusionIndexAccessor );
    }

    @Test
    public void closeMustCloseOthersIfLuceneThrow() throws Exception
    {
        verifyOtherIsClosedOnSingleThrow( luceneAccessor, fusionIndexAccessor, nativeAccessor, spatialAccessor );
    }

    @Test
    public void closeMustCloseOthersIfSpatialThrow() throws Exception
    {
        verifyOtherIsClosedOnSingleThrow( spatialAccessor, fusionIndexAccessor, nativeAccessor, luceneAccessor );
    }

    @Test
    public void closeMustCloseOthersIfNativeThrow() throws Exception
    {
        verifyOtherIsClosedOnSingleThrow( nativeAccessor, fusionIndexAccessor, luceneAccessor, spatialAccessor );
    }

    @Test
    public void closeMustThrowIfAllFail() throws Exception
    {
        verifyFusionCloseThrowIfAllThrow( fusionIndexAccessor, nativeAccessor, spatialAccessor, luceneAccessor );
    }

    // newAllEntriesReader

    @Test
    public void allEntriesReaderMustCombineResultFromNativeAndLucene()
    {
        // given
        long[] nativeEntries = {0, 1, 2, 5, 6};
        long[] luceneEntries = {3, 4, 7, 8};
        mockAllEntriesReaders( nativeEntries, luceneEntries );

        // when
        Set<Long> result = Iterables.asSet( fusionIndexAccessor.newAllEntriesReader() );

        // then
        assertResultContainsAll( result, nativeEntries );
        assertResultContainsAll( result, luceneEntries );
    }

    @Test
    public void allEntriesReaderMustCombineResultFromNativeAndLuceneWithEmptyNative()
    {
        // given
        long[] nativeEntries = new long[0];
        long[] luceneEntries = {3, 4, 7, 8};
        mockAllEntriesReaders( nativeEntries, luceneEntries );

        // when
        Set<Long> result = Iterables.asSet( fusionIndexAccessor.newAllEntriesReader() );

        // then
        assertResultContainsAll( result, nativeEntries );
        assertResultContainsAll( result, luceneEntries );
    }

    @Test
    public void allEntriesReaderMustCombineResultFromNativeAndLuceneWithEmptyLucene()
    {
        // given
        long[] nativeEntries = {0, 1, 2, 5, 6};
        long[] luceneEntries = new long[0];
        mockAllEntriesReaders( nativeEntries, luceneEntries );

        // when
        Set<Long> result = Iterables.asSet( fusionIndexAccessor.newAllEntriesReader() );

        // then
        assertResultContainsAll( result, nativeEntries );
        assertResultContainsAll( result, luceneEntries );
    }

    @Test
    public void allEntriesReaderMustCombineResultFromNativeAndLuceneBothEmpty()
    {
        // given
        long[] nativeEntries = new long[0];
        long[] luceneEntries = new long[0];
        mockAllEntriesReaders( nativeEntries, luceneEntries );

        // when
        Set<Long> result = Iterables.asSet( fusionIndexAccessor.newAllEntriesReader() );

        // then
        assertResultContainsAll( result, nativeEntries );
        assertResultContainsAll( result, luceneEntries );
        assertTrue( result.isEmpty() );
    }

    @Test
    public void allEntriesReaderMustCloseBothNativeAndLucene() throws Exception
    {
        // given
        BoundedIterable<Long> nativeAllEntriesReader = mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        BoundedIterable<Long> spatialAllEntriesReader = mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        BoundedIterable<Long> luceneAllEntriesReader = mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // when
        fusionIndexAccessor.newAllEntriesReader().close();

        // then
        verify( nativeAllEntriesReader, times( 1 ) ).close();
        verify( spatialAllEntriesReader, times( 1 ) ).close();
        verify( luceneAllEntriesReader, times( 1 ) ).close();
    }

    @Test
    public void allEntriesReaderMustCloseNativeIfLuceneThrow() throws Exception
    {
        // given
        BoundedIterable<Long> nativeAllEntriesReader = mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        BoundedIterable<Long> spatialAllEntriesReader = mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        BoundedIterable<Long> luceneAllEntriesReader = mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        verifyOtherIsClosedOnSingleThrow( luceneAllEntriesReader, fusionAllEntriesReader, nativeAllEntriesReader, spatialAllEntriesReader );
    }

    @Test
    public void allEntriesReaderMustCloseLuceneIfSpatialThrow() throws Exception
    {
        // given
        BoundedIterable<Long> nativeAllEntriesReader = mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        BoundedIterable<Long> spatialAllEntriesReader = mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        BoundedIterable<Long> luceneAllEntriesReader = mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        verifyOtherIsClosedOnSingleThrow( spatialAllEntriesReader, fusionAllEntriesReader, luceneAllEntriesReader, nativeAllEntriesReader );
    }

    @Test
    public void allEntriesReaderMustCloseLuceneIfNativeThrow() throws Exception
    {
        // given
        BoundedIterable<Long> nativeAllEntriesReader = mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        BoundedIterable<Long> spatialAllEntriesReader = mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        BoundedIterable<Long> luceneAllEntriesReader = mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        verifyOtherIsClosedOnSingleThrow( nativeAllEntriesReader, fusionAllEntriesReader, luceneAllEntriesReader, spatialAllEntriesReader );
    }

    @Test
    public void allEntriesReaderMustThrowIfLuceneThrow() throws Exception
    {
        // given
        mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        BoundedIterable<Long> luceneAllEntriesReader = mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        FusionIndexTestHelp.verifyFusionCloseThrowOnSingleCloseThrow( luceneAllEntriesReader, fusionAllEntriesReader );
    }

    @Test
    public void allEntriesReaderMustThrowIfNativeThrow() throws Exception
    {
        // given
        BoundedIterable<Long> nativeAllEntriesReader = mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        FusionIndexTestHelp.verifyFusionCloseThrowOnSingleCloseThrow( nativeAllEntriesReader, fusionAllEntriesReader );

    }

    @Test
    public void allEntriesReaderMustThrowIfSpatialThrow() throws Exception
    {
        // given
        mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        BoundedIterable<Long> spatialAllEntriesReader = mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        FusionIndexTestHelp.verifyFusionCloseThrowOnSingleCloseThrow( spatialAllEntriesReader, fusionAllEntriesReader );

    }

    @Test
    public void allEntriesReaderMustReportUnknownMaxCountIfNativeReportUnknownMaxCount()
    {
        // given
        mockSingleAllEntriesReaderWithUnknownMaxCount( nativeAccessor, new long[0] );
        mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        assertThat( fusionAllEntriesReader.maxCount(), is( BoundedIterable.UNKNOWN_MAX_COUNT ) );
    }

    @Test
    public void allEntriesReaderMustReportUnknownMaxCountIfSpatialReportUnknownMaxCount() throws Exception
    {
        // given
        mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        mockSingleAllEntriesReaderWithUnknownMaxCount( spatialAccessor, new long[0] );
        mockSingleAllEntriesReader( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        assertThat( fusionAllEntriesReader.maxCount(), is( BoundedIterable.UNKNOWN_MAX_COUNT ) );
    }

    @Test
    public void allEntriesReaderMustReportUnknownMaxCountIfLuceneReportUnknownMaxCount()
    {
        // given
        mockSingleAllEntriesReader( nativeAccessor, new long[0] );
        mockSingleAllEntriesReader( spatialAccessor, new long[0] );
        mockSingleAllEntriesReaderWithUnknownMaxCount( luceneAccessor, new long[0] );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        assertThat( fusionAllEntriesReader.maxCount(), is( BoundedIterable.UNKNOWN_MAX_COUNT ) );
    }

    @Test
    public void allEntriesReaderMustReportFusionMaxCountOfNativeAndLucene()
    {
        mockSingleAllEntriesReader( nativeAccessor, new long[]{1, 2} );
        mockSingleAllEntriesReader( spatialAccessor, new long[]{3, 4} );
        mockSingleAllEntriesReader( luceneAccessor, new long[]{5, 6} );

        // then
        BoundedIterable<Long> fusionAllEntriesReader = fusionIndexAccessor.newAllEntriesReader();
        assertThat( fusionAllEntriesReader.maxCount(), is( 6L ) );
    }

    static void assertResultContainsAll( Set<Long> result, long[] nativeEntries )
    {
        for ( long nativeEntry : nativeEntries )
        {
            assertTrue( "Expected to contain " + nativeEntry + ", but was " + result, result.contains( nativeEntry ) );
        }
    }

    static BoundedIterable<Long> mockSingleAllEntriesReader( IndexAccessor targetAccessor, long[] entries )
    {
        BoundedIterable<Long> allEntriesReader = mockedAllEntriesReader( entries );
        when( targetAccessor.newAllEntriesReader() ).thenReturn( allEntriesReader );
        return allEntriesReader;
    }

    static BoundedIterable<Long> mockedAllEntriesReader( long... entries )
    {
        return mockedAllEntriesReader( true, entries );
    }

    static BoundedIterable<Long> mockSingleAllEntriesReaderWithUnknownMaxCount( IndexAccessor targetAccessor, long[] entries )
    {
        BoundedIterable<Long> allEntriesReader = mockedAllEntriesReaderUnknownMaxCount( entries );
        when( targetAccessor.newAllEntriesReader() ).thenReturn( allEntriesReader );
        return allEntriesReader;
    }

    static BoundedIterable<Long> mockedAllEntriesReaderUnknownMaxCount( long... entries )
    {
        return mockedAllEntriesReader( false, entries );
    }

    static BoundedIterable<Long> mockedAllEntriesReader( boolean knownMaxCount, long... entries )
    {
        BoundedIterable<Long> mockedAllEntriesReader = mock( BoundedIterable.class );
        when( mockedAllEntriesReader.maxCount() ).thenReturn( knownMaxCount ? entries.length : BoundedIterable.UNKNOWN_MAX_COUNT );
        when( mockedAllEntriesReader.iterator() ).thenReturn( Iterators.asIterator(entries ) );
        return mockedAllEntriesReader;
    }

    private void mockAllEntriesReaders( long[] nativeEntries, long[] luceneEntries )
    {
        mockSingleAllEntriesReader( nativeAccessor, nativeEntries );
        mockSingleAllEntriesReader( spatialAccessor, nativeEntries );
        mockSingleAllEntriesReader( luceneAccessor, luceneEntries );
    }
}
