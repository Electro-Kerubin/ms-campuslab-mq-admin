package org.campuslab.mqadmin.dto;

/**
 * CLASSIC: cola clásica, vive en el nodo donde se declara.
 * QUORUM: cola replicada entre los nodos del clúster (la que se usa para alta disponibilidad).
 */
public enum QueueType {
    CLASSIC,
    QUORUM
}
