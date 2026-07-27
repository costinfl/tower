# Event Model

**Status:** Draft

**Owner:** Architecture

**Related:**

- Domain-Model.md
- CRC-Cards.md
- State-Model.md

---

# Purpose

This document defines the business events that occur within the Tower domain.

Events describe meaningful changes in business state.

The Event Model is independent of implementation technologies such as message brokers, event buses or databases.

Events are expressed in business language.

---

# Event Principles

- Events describe something that has already happened.
- Events are immutable.
- Events use past tense.
- Events represent business facts.
- Events may originate from either Observations or developer Intent.
- Events do not describe implementation details.

---

# Event Categories

Tower recognizes two categories of events.

## Observation Events

Generated after information has been collected from External Systems.

Observation Events originate from facts.

---

## Domain Events

Generated after Tower updates its Canonical Model.

Domain Events describe changes within Tower's own business domain.

---

# Observation Events

## Environment Observed

### Description

A Collector has successfully observed an Environment.

### Producer

Collector

### Consumers

- Store
- Viewer

---

## Application Version Observed

### Description

A deployed Application Version has been discovered.

### Producer

Collector

### Consumers

- Store
- Release Pack correlation

---

## Deployment Unit Observed

### Description

A Deployment Unit has been detected inside an Environment.

### Producer

Collector

### Consumers

- Store
- Snapshot creation

---

## External System Synchronized

### Description

Information from an External System has been successfully collected.

### Producer

Collector

### Consumers

- Store

---

# Domain Events

## Release Pack Created

### Description

A new Release Pack has been defined.

### Producer

Developer

### Consumers

- Store
- Viewer

---

## Release Pack Updated

### Description

Release metadata has changed.

### Producer

Developer

### Consumers

- Store
- Viewer

---

## Application Added to Release Pack

### Description

An Application Version has been associated with a Release Pack.

### Producer

Developer

### Consumers

- Store
- Documentation generation

---

## Application Removed from Release Pack

### Description

An Application Version has been removed from a Release Pack.

### Producer

Developer

### Consumers

- Store

---

## Promotion Path Assigned

### Description

A Promotion Path has been selected for a Release Pack.

### Producer

Developer

### Consumers

- Store
- Viewer

---

## Handover Updated

### Description

Developer-owned deployment information has changed.

### Producer

Developer

### Consumers

- Documentation generation

---

## Snapshot Created

### Description

A Snapshot has been created from current Observations.

### Producer

Store

### Consumers

- Viewer
- Historical comparison

---

# Event Flow

## Environment Observation

```
Collector
        │
        ▼
Environment Observed
        │
        ▼
Application Version Observed
        │
        ▼
Deployment Unit Observed
        │
        ▼
Snapshot Created
```

---

## Release Preparation

```
Developer
        │
        ▼
Release Pack Created
        │
        ▼
Application Added to Release Pack
        │
        ▼
Promotion Path Assigned
        │
        ▼
Handover Updated
```

---

# Event Ordering

For a typical synchronization cycle the expected order is:

1. External System Synchronized
2. Environment Observed
3. Application Version Observed
4. Deployment Unit Observed
5. Snapshot Created

For a typical release preparation cycle the expected order is:

1. Release Pack Created
2. Application Added to Release Pack
3. Promotion Path Assigned
4. Handover Updated

---

# Business Rules

EV-01

Events are immutable.

---

EV-02

Events describe completed business actions.

---

EV-03

Observation Events originate from External Systems.

---

EV-04

Domain Events originate from Tower.

---

EV-05

Events never execute deployments.

---

EV-06

Events never modify External Systems.

---

EV-07

Every Snapshot is created from Observations.

---

EV-08

Generated documentation shall originate from the Canonical Model rather than directly from Events.

---

# Out of Scope

The Event Model intentionally excludes:

- event bus implementation;
- messaging technologies;
- Kafka;
- RabbitMQ;
- asynchronous processing;
- retry mechanisms;
- persistence mechanisms.

These concerns belong to the implementation architecture.
