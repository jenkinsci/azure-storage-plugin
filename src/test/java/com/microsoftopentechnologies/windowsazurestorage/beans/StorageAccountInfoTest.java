/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.microsoftopentechnologies.windowsazurestorage.beans;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arroyc
 */
class StorageAccountInfoTest {

    private StorageAccountInfo st;
    private final String stName = "test1";
    private final String stKey = "123";
    private final String stBlobURL = "http://test1";
    private final String stCdnURL = "http://cdn-test1";

    @BeforeEach
    void setUp() {
        st = new StorageAccountInfo(stName, stKey, stBlobURL, stCdnURL);
    }

    /**
     * Test of getStorageAccName method, of class StorageAccountInfo.
     */
    @Test
    void testGetStorageAccName() {
        System.out.println("getStorageAccName");
        StorageAccountInfo instance = st;
        assertEquals(stName, instance.getStorageAccName());
    }

    /**
     * Test of setStorageAccName method, of class StorageAccountInfo.
     */
    @Test
    void testSetStorageAccName() {
        System.out.println("setStorageAccName");
        StorageAccountInfo instance = st;
        assertEquals(stName, instance.getStorageAccName());
        instance.setStorageAccName("zzz");
        assertEquals("zzz", instance.getStorageAccName());
    }

    /**
     * Test of getStorageAccountKey method, of class StorageAccountInfo.
     */
    @Test
    void testGetStorageAccountKey() {
        System.out.println("getStorageAccountKey");
        StorageAccountInfo instance = st;
        assertEquals(stKey, instance.getStorageAccountKey());
    }

    /**
     * Test of setStorageAccountKey method, of class StorageAccountInfo.
     */
    @Test
    void testSetStorageAccountKey() {
        System.out.println("setStorageAccountKey");
        String storageAccountKey = "999";
        StorageAccountInfo instance = st;
        assertEquals(stKey, instance.getStorageAccountKey());
        instance.setStorageAccountKey(storageAccountKey);
        assertEquals("999", instance.getStorageAccountKey());
    }

    /**
     * Test of getBlobEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    void testGetBlobEndPointURL() {
        System.out.println("getBlobEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stBlobURL, instance.getBlobEndPointURL());
    }

    /**
     * Test of setBlobEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    void testSetBlobEndPointURL() {
        System.out.println("setBlobEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stBlobURL, instance.getBlobEndPointURL());
        instance.setBlobEndPointURL("abcd");
        assertEquals("abcd", instance.getBlobEndPointURL());
    }

    /**
     * Test of getCdnEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    void testGetCdnEndPointURL() {
        System.out.println("getCdnEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stCdnURL, instance.getCdnEndPointURL());
    }

    /**
     * Test of setCdnEndPointURL method, of class StorageAccountInfo.
     */
    @Test
    void testSetCdnEndPointURL() {
        System.out.println("setCdnEndPointURL");
        StorageAccountInfo instance = st;
        assertEquals(stCdnURL, instance.getCdnEndPointURL());
        instance.setCdnEndPointURL("cdn-123");
        assertEquals("cdn-123", instance.getCdnEndPointURL());
    }
}
